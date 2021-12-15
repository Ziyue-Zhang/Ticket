# ticketingsystem

### 1 设计思路

#### 1.1 并发数据结构

##### 1.1.1 座位表

```java
CopyOnWriteArrayList<CopyOnWriteArrayList<AtomicInteger>> routes;
```

采用二维数组实现座位表，通过路线号和座位号索引，座位号通过(coach - 1) * seatnum + (seat - 1)计算获得。该二维数组记录了座位是否被占用的信息数据，座位信息数据使用AtomicInteger类型，通过2bit数记录火车从起点到终点的座位情况，其中1表示有座，0表示无座。若火车一共有10站，则用二进制的111111111来表示，表示这10站对应的9个区间都有座位，如果买了1-3站的票，则修改为001111111。通过该数据结构，大大提升了买票和退票时判断是否能进行操作以及买票退票操作的速度。

##### 1.1.2 余票表

```java
CopyOnWriteArrayList<CopyOnWriteArrayList<AtomicInteger>> left_routes;
```

采用二维数组实现座位表，通过路线号、火车起点、火车终点进行索引。其中departure*stationnum + arrival构成了数组的第二个维度。该二维数组记录了座位的余票数据，查询操作直接通过查询该二维数组进行余票的查询，时间复杂度为o(1)，购票、退票操作需将受到影响的所有区间的余票都进行+1或-1的修改，时间复杂度为o(n^2)，同时会随着购票的进行，所需修改的区间逐渐变少。具体的实现如下：

先获得需要修改的区间范围

```java
				int low = departure;
        int high = arrival+1;
        int mask = 0;
        while(low >= 1){
            mask = 1 << (stationnum - low);
            if((mask & ticket_mask) == mask){
                low -= 1;
            }
            else{
                break;
            }
        }
        while(high <= stationnum){
            mask = 1 << (stationnum - high);
            if((mask & ticket_mask) == mask){
                high += 1;
            }
            else{
                break;
            }
        }
```

再对需要修改的区间进行修改

```java
			  CopyOnWriteArrayList<AtomicInteger>thisroute = routes.get(route - 1);

        for(int i = departure+1; i < arrival; i++){
            for (int j = i+1; j < high; j++){
                thisroute.get(i*stationnum + j).incrementAndGet();
            }
        }

        for(int i = low; i <= departure; i++){
            for (int j = departure+1; j < high; j++){
                thisroute.get(i*stationnum + j).incrementAndGet();
            }
        }
```

未使用余票表的时候，需要在每一次查询操作的时候遍历一辆火车的全部座位，时间复杂度为o(n)，使用余票表后可以将查询的时间复杂度降为o(1)。同时购票和退票也需要维护退票表，他们的时间复杂度为o(n^2)，但是随着票逐渐售磬，维护购票、退票的时间复杂度也降为o(1)，且查询操作占比更大。因此相比于不使用余票表的实现，使用余票表后，火车票管理系统的性能可以大幅提升。

##### 1.1.3 购票表

```java
CopyOnWriteArrayList<ArrayList<ArrayList<MyTicket>>> sold_routes;
```

采用二维数组实现，通过车次、座位号、火车起点作为索引，MyTicket中记录了tid、passenger、arrival信息，该结构用来对假退票进行判断。

```java
class MyTicket{
	volatile long tid;
	volatile int arrival;
	volatile String passenger;
	MyTicket(long tid, String passenger, int arrival){
		this.tid = tid;
		this.arrival = arrival;
		this.passenger = passenger;
	}
	MyTicket(){
		this.tid = (long) 0;
		this.arrival = 0;
		this.passenger = "";
	}
}
```

根据车票的唯一性，一张车票与车次号+座位号+起点一一对应，因此我们可以用车次号+座位号+起点去映射一张车票信息，同时由于车次号、座位号、起点已经座位索引，车票信息只需要再保存tid、arrival和passenger即可，大大减少了存储空间的要求。由于使用了数组，因此在修改或查询时的时间复杂度均为o(1)，对性能不产生任何影响。该购票表中的数据采用volatile类型，保证修改对其他线程可见，同时由于对购票表的修改在购票的CAS成功之后，CAS操作可以提供一个和spinlock一样的保护，因此在修改购票表的时候不用使用锁。

为防止假退票时不修改Ticket对象等情况的发生，在退票进行检查时通过内容逐个比较进行判断，并对passenger进行equal比较，保证检查对正确性。

```java
		ArrayList<ArrayList<MyTicket>>sold = sold_routes.get(route - 1);
		if(sold.get(loc).get(departure).tid != ticket.tid 
		|| sold.get(loc).get(departure).arrival != arrival
		|| sold.get(loc).get(departure).passenger.equals(ticket.passenger) == false){
			return false;
		}
```

#### 1.2 购票、退票、查询余票的方法

##### 1.2.1 购票方法

1. 遍历座位表数组，座位变量使用原子整型，购票方法通过CAS实现，通过while循环不断尝试对某个有空位的座位进行CAS操作，仅当CAS操作成功时才成功购票并返回，类似于spinlock的实现，同时在while循环中如果检测到该座位不再有空位则从while循环中跳出，对下一个相邻座位执行相同的操作。若所有座位都被遍历之后仍没有买到票，则返回null，意味着未买到票。
2. 为了避免多个线程同时调用购票方法，导致同时争用同一个原子整型变量，因此采用随机方法来生成第一个要访问的座位号，大大降低出现上述问题的概率。同时，为了避免多个线程争用同一个随机函数，使用ThreadLocalRandom类来避免该问题的发生。
3. 每当CAS操作成功之后，维护余票表数组，通过向修改函数传入车次号、车票的起点、车票的终点，修改受到影响的所有区间的余票数量，对这些区间的余票数量减1。
4. 每当CAS操作成功之后，还需要维护一个购票表数组，记录该票的tid、passenger等信息，能够在退票时对他们进行检测，避免出现假退票的现象。

##### 1.2.2 退票方法

1. 先检查购票表，如果发现passenger信息、tid信息与之前发生变化，则意味着假退票发生，直接返回false。
2. 遍历座位表数组，座位变量使用原子整型，购票方法通过CAS实现，通过while循环不断尝试对某个有空位的座位进行CAS操作，仅当CAS操作成功时才成功购票并返回，类似于spinlock的实现，同时在while循环中如果检测到该座位不再有空位则从while循环中跳出，对下一个相邻座位执行相同的操作。若所有座位都被遍历之后仍没有买到票，则返回null，意味着未买到票。
3. 每当CAS操作成功之后，维护余票表数组，通过向修改函数传入车次号、车票的起点、车票的终点，修改受到影响的所有区间的余票数量，对这些区间的余票数量加1。

##### 1.2.3 查询余票方法

通过车次号、起点、终点信息调用余票表的查询余票函数，该函数根据该信息直接访问余票表数组，返回查询到的余票结果。

**在这三个方法的实现中，都会先调用check函数来检测路线号、座位号等信息，确保他们的合法性，从而保证程序的正确性。**

#### 1.3 多线程测试程序

通过数组单独记录每一次购票、退票、查票的时间，将所有的购票时间求和、退票时间求和、查票时间求和，计算对应操作的延迟，同时通过这些操作的总时间，计算最终的吞吐率。

### 2 性能

#### 2.1 性能测试

性能测试时的参数配置如下：

```java
				final static int threadnum = 64;
        final static int routenum = 10; // route is designed from 1 to 3
        final static int coachnum = 10; // coach is arranged from 1 to 5
        final static int seatnum = 100; // seat is allocated from 1 to 20
        final static int stationnum = 20; // station is designed from 1 to 5

        final static int testnum = 100000;
        final static int retpc = 10; // return ticket operation is 10% percent
        final static int buypc = 30; // buy ticket operation is 20% percent
        final static int inqpc = 100;
```

在服务器的64线程下，每ms吞吐量可以达到18000左右

#### 2.2 性能分析

使用余票表后可以将查询的时间复杂度降为o(1)，同时查询的占比更大，因此购票和退票对余票表修改的时间复杂度可以均摊到查询操作上，不会达到理论上的o(n^2)。总的时间复杂度，相比于不使用余票表的实现降低了很多。通过延迟的比较，可以观察到退票和购票的延迟虽然相差2-3倍左右，而查票操作的延迟相差6-10倍。因此在查询占比较大的时候，使用余票表后，可以提升很大的性能。

### 3 正确性分析

#### 3.1 可线性化

##### 3.1.1 购票方法

座位变量使用原子整型，因此购票方法通过CAS实现，通过while循环不断尝试对某个有空位的座位进行CAS操作，仅当CAS操作成功时才成功购票并返回。因此座位变量是互斥访问的。可线性化点在线程CAS成功的地方。因此购票方法是可线性化的。

##### 3.1.2 退票方法

同购票方法一样，退票方法通过CAS实现，在while循环不断尝试对某个需要退票的位置进行CAS操作，仅当CAS操作成功时才成功退票并返回。座位变量是互斥访问的，可线性化点在线程CAS成功的地方，因此退票方法也是可线性化的。

#### 3.2 静态一致性

##### 3.2.1 查询余票方法

查询余票直接通过查询余票表的方法进行查询，余票表中的余票数量通过购票方法和退票方法调用相应的修改函数进行维护。通过向修改函数传入车次号、车票的起点、车票的终点，修改受到影响的所有区间的余票数量。由于通过for循环遍历的方法对这些区间进行修改，所以在查询余票的时候，如果其他线程调用了购票方法或退票方法，即查询的同时余票表被修改的情况下，既有可能查到修改前的结果，也有可能查到修改后的结果，是不准确的。例如，不妨设1 5区间的余票为0，线程A先查询1 3区间的余票，再查询3 5区间的余票，同时线程B对1 5区间进行退票操作，并修改余票表。此时可能出现线程A先执行的对1 3区间的查询返回1，后执行的对3 5区间的查询返回0。这是由于线程B对余票表的修改可能有延迟。若线程A满足可线性化条件，则先执行的查询返回1时，后执行的查询也应该返回1，与实际不符合。因此该查询操作在和其他购票和退票重叠执行的时候，是不准确的。

同时，在查询余票的时候，没有其他线程调用了购票方法或退票方法，即没有其他线程在修改余票表，余票的查询时准确的。

因此查询余票方法是静态一致的。

#### 3.3 lock-free

##### 3.3.1 购票方法

购票方法通过CAS实现，通过while循环不断尝试对某个有空位的座位进行CAS操作，仅当CAS操作成功时才成功购票并返回，同时在while循环中如果检测到该座位不再有空位则从while循环中跳出，对下一个相邻座位执行相同的操作。其中CAS操作使得购票方法时lock-free的，因为总有一个CAS操作能够成功，可以返回买到的票，因此是lock-free的。同时如果这个座位不断的有其他线程在进行购票操作和退票操作，且他们的CAS操作一直成功。该线程会不断的在while循环中执行CAS操作，由于CAS一直失败，无法买到票，不能保证所有线程都成功买到票。因此购票方法不是wait-free的。

##### 3.3.2 退票方法

退票方法也通过CAS实现，在while循环不断尝试对某个需要退票的位置进行CAS操作，仅当CAS操作成功时才成功退票并返回。由于CAS操作保证了总有一个线程的CAS操作是成功的，可以成功退票，因此该方法时lock-free的。同时如果这个座位不断的有其他线程在进行购票操作和退票操作，且他们的CAS操作一直成功。该线程会不断的在while循环中执行CAS操作，由于CAS一直失败，将无法退票，不能保证所有线程都退票成功。因此退票方法不是wait-free的。

#### 3.4 wait-free

##### 3.4.1 查询余票方法

查询余票直接通过查询余票表的方法进行查询，通过路线号、起点、终点作为索引来访问余票数组，余票信息用AtomicInteger保存，调用get函数获得对应的int值，不会被其他线程阻塞，因此是wait-free的。
