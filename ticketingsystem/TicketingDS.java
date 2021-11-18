package ticketingsystem;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
//import java.util.concurrent.locks.ReentrantLock;
//import java.util.ArrayList;

public class TicketingDS implements TicketingSystem {
	int routenum = 5;
	int coachnum = 8;
	int seatnum = 100; 
	int stationnum = 10;
	int threadnum = 16;

	int maxnum;
	long stationmask;
    
	public static AtomicLong count = new AtomicLong(0);
	CopyOnWriteArrayList<CopyOnWriteArrayList<AtomicLong>> routes;

	// cache for inquiry
	ConcurrentHashMap<Integer, ConcurrentHashMap<Long,Integer>> cache;
    // CopyOnWriteArrayList<AtomicLong> dirty;

	//ReentrantLock rtLock = new ReentrantLock();
	
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

		stationmask = (1 << (stationnum-1)) - 1;
		
		routes = new CopyOnWriteArrayList<>();
		cache = new ConcurrentHashMap<Integer, ConcurrentHashMap<Long,Integer>>();
		for(int i = 0; i < routenum; i++) {
			CopyOnWriteArrayList<AtomicLong>seats = new CopyOnWriteArrayList<>();
			for(int j = 0; j < maxnum; j++) {
				seats.add(new AtomicLong(stationmask));
			}
			routes.add(seats);

			for(int j = 0; j < coachnum; j++) {
				ConcurrentHashMap<Long, Integer> tmp = new ConcurrentHashMap<Long,Integer>(seatnum*3);
				tmp.put(stationmask, seatnum);
				cache.put(i*coachnum+j,tmp);
			}
			// dirty.add(new AtomicLong(1));
		}
	}


	@Override
	public Ticket buyTicket(String passenger, int route, int departure, int arrival) {

		long partmask1 = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));
		long partmask2 = stationmask & (~partmask1);

		Ticket ticket = new Ticket();
		ticket.tid = count.incrementAndGet();
		ticket.passenger = passenger;
		ticket.route = route;
		ticket.departure = departure;
		ticket.arrival = arrival;

		CopyOnWriteArrayList<AtomicLong>thisroute = routes.get(route - 1);
		//ConcurrentHashMap<Long,Integer>thiscache = cache.get(route - 1);
		
		//rtLock.lock();
		for(int i = 0; i < maxnum; i++){
			long seatmask = thisroute.get(i).get();
			while((seatmask & partmask1) == partmask1){
				if(thisroute.get(i).compareAndSet(seatmask, seatmask & partmask2)){
					ticket.coach = i / seatnum + 1;
					ticket.seat = i % seatnum + 1;
					cache.get((route - 1) * coachnum + ticket.coach - 1).clear();
					return ticket;
				}
				seatmask = thisroute.get(i).get();
			}
		}
		//rtLock.unlock();		

		return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		int ans = 0;
		int tmp = 0;

		final long partmask = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));

		CopyOnWriteArrayList<AtomicLong>thisroute = routes.get(route - 1);
		//ConcurrentHashMap<Long,Integer>thiscache = cache.get(route - 1);

		int loc = (route-1)*coachnum;
		int value = 0;
		for(int j = 0; j< coachnum; j++){
			try{
				value = cache.get(loc+j).get(partmask);
				tmp = value;
			}catch(NullPointerException e){
				for(int i = 0; i < maxnum; i++){
					long seatmask = thisroute.get(i).get();
					if((seatmask & partmask) == partmask){
						tmp++;
					}
				}
			   }finally{
				cache.get(loc+j).put(partmask, tmp);
				ans+=tmp;
			   }
		}
		
		//rtLock.readLock().lock();
		/*for(int i = 0; i < maxnum; i++){
			long seatmask = thisroute.get(i).get();
			if((seatmask & partmask) == partmask){
				ans++;
			}
		}*/
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

		long partmask1 = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));
		long partmask2 = stationmask & (~partmask1);

		CopyOnWriteArrayList<AtomicLong>thisroute = routes.get(route - 1);
		//ConcurrentHashMap<Long,Integer>thiscache = cache.get(route - 1);

		//rtLock.lock();
		long seatmask = thisroute.get(loc).get();
		while((seatmask | partmask2) == partmask2){
			if(thisroute.get(loc).compareAndSet(seatmask, seatmask | partmask1)){
				cache.get((route - 1) * coachnum + coach - 1).clear();
				return true;
			}
			seatmask = thisroute.get(loc).get();
		}
		//rtLock.unlock();

		return false;
	}

}
