package ticketingsystem;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Vector;

public class TicketingDS implements TicketingSystem {
	int routenum = 5;
	int coachnum = 8;
	int seatnum = 100; 
	int stationnum = 10;
	int threadnum = 16;

    int maxnum;
	int stationmask;
    
	public static AtomicLong count = new AtomicLong(0);
	Vector<Vector<Integer>> data;
    
	ReentrantReadWriteLock rtLock = new ReentrantReadWriteLock();
	
    TicketingDS(){
		init();
	}

	TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum){
		this.routenum = routenum;
		this.coachnum = coachnum;
		this.seatnum = seatnum;
		this.stationnum = stationnum;
		this.threadnum = threadnum;
		init();
	}
    
	void init(){
		maxnum = coachnum * seatnum;
        /*stationmask = 0;
		for(int i = 0; i < stationnum; i++){
			stationmask = (stationmask << 1) + 1;
		}*/
		stationmask = (1 << (stationnum-1)) - 1;
		
		data = new Vector<Vector<Integer>>();
		for(int i = 0; i < routenum; i++) {
			Vector<Integer>temp = new Vector<Integer>();
			for(int j = 0; j < maxnum; j++) {
				temp.add(stationmask);
			}
			data.add(temp);
		}
	}


	@Override
	public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
		
		long tid = count.incrementAndGet();

		/*int partmask1 = 0;
		int partmask2 = 0;
		for(int i = 0; i < stationnum; i++){
			partmask1 = partmask1 << 1;
			partmask2 = partmask2 << 1;
			if(departure - 1 <= i && i <= arrival - 1)
				partmask1 += 1;
			else
				partmask2 += 1;
		} */
		int partmask1 = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));
		int partmask2 = stationmask & (~partmask1);

		Ticket ticket = new Ticket();
		ticket.tid = tid;
		ticket.passenger = passenger;
		ticket.route = route;
		ticket.departure = departure;
		ticket.arrival = arrival;

		Vector<Integer>thisroute = data.get(route - 1);

		rtLock.writeLock().lock();
		boolean flag = false;
		for(int i = 0; i < maxnum; i++){
			int temp = thisroute.get(i);
			if((temp & partmask1) == partmask1){
				thisroute.setElementAt(temp & partmask2, i);
				flag = true;
				ticket.coach = i / seatnum + 1;
				ticket.seat = i % seatnum + 1;
				break;
			}
		}
		rtLock.writeLock().unlock();		

		if(flag){
			return ticket;
		}
		else
			return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		int ans= 0;

		/*int partmask = 0;
		for(int i = 0; i < stationnum; i++){
			partmask = partmask << 1;
			if(departure - 1 <= i && i <= arrival - 1)
				partmask += 1;
		}*/
		int partmask = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));

		Vector<Integer>thisroute = data.get(route - 1);

		//rtLock.readLock().lock();
		for(int i = 0; i < maxnum; i++){
			int temp = thisroute.get(i);
			if((temp & partmask) == partmask){
				ans++;
			}
		}
		//rtLock.readLock().unlock();

		return ans;
	}

	@Override
	public boolean refundTicket(Ticket ticket) {
		int route = ticket.route;
		int departure = ticket.departure;
		int arrival = ticket.arrival;
		int coach = ticket.coach;
		int seat = ticket.seat;

		int loc = (coach - 1) * seatnum + (seat - 1);

		/*int partmask1 = 0;
		int partmask2 = 0;
		for(int i = 0; i < stationnum; i++){
			partmask1 = partmask1 << 1;
			partmask2 = partmask2 << 1;
			if(departure - 1 <= i && i <arrival - 1)
				partmask1 += 1;
			else
				partmask2 += 1;
		}*/
		int partmask1 = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));
		int partmask2 = stationmask & (~partmask1);

		Vector<Integer>thisroute = data.get(route - 1);

		rtLock.writeLock().lock();
		boolean flag = false;
		int temp = thisroute.get(loc);
		if((temp | partmask2) == partmask2){
			flag = true;
			thisroute.setElementAt(temp | partmask1, loc);
		}
		rtLock.writeLock().unlock();

		return flag;
	}


		

}
