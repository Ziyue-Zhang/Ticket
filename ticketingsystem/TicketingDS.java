package ticketingsystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
//import java.util.concurrent.locks.ReentrantLock;
//import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class TicketingDS implements TicketingSystem {
	int routenum = 5;
	int coachnum = 8;
	int seatnum = 100; 
	int stationnum = 10;
	int threadnum = 16;

	int maxnum;
	int stationmask;
    
	public static AtomicInteger count = new AtomicInteger(0);
	CopyOnWriteArrayList<CopyOnWriteArrayList<AtomicInteger>> routes;
	CopyOnWriteArrayList<CopyOnWriteArrayList<AtomicLong>> solds;
	TicketingCounter tc;
    
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
		for(int i = 0; i < routenum; i++) {
			CopyOnWriteArrayList<AtomicInteger>seats = new CopyOnWriteArrayList<>();
			for(int j = 0; j < maxnum; j++) {
				seats.add(new AtomicInteger(stationmask));
			}
			routes.add(seats);
		}

		solds = new CopyOnWriteArrayList<>();
		for(int i = 0; i < routenum; i++) {
			CopyOnWriteArrayList<AtomicLong>sold = new CopyOnWriteArrayList<>();
			for(int j = 0; j < maxnum; j++) {
				sold.add(new AtomicLong(0));
			}
			solds.add(sold);
		}

		tc = new TicketingCounter(routenum, maxnum, stationnum);
	}


	@Override
	public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
		if(!legal(route, departure, arrival))
			return null;

		int partmask1 = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));
		int partmask2 = stationmask & (~partmask1);

		Ticket ticket = new Ticket();
		ticket.tid = count.incrementAndGet();
		ticket.passenger = passenger;
		ticket.route = route;
		ticket.departure = departure;
		ticket.arrival = arrival;

		int rand_i = ThreadLocalRandom.current().nextInt(maxnum);

		CopyOnWriteArrayList<AtomicInteger>thisroute = routes.get(route - 1);
		CopyOnWriteArrayList<AtomicLong>sold = solds.get(route - 1);
		
		//rtLock.lock();
		for(int i = rand_i; i < maxnum; i++){
			int seatmask = thisroute.get(i).get();
			while((seatmask & partmask1) == partmask1){
				if(thisroute.get(i).compareAndSet(seatmask, seatmask & partmask2)){
					ticket.coach = i / seatnum + 1;
					ticket.seat = i % seatnum + 1;
					sold.get(i).set(ticket.tid);
					tc.buyticket(route, departure, arrival, seatmask);
					return ticket;
				}
				seatmask = thisroute.get(i).get();
			}
		}
		
		for(int i = 0; i < rand_i; i++){
			int seatmask = thisroute.get(i).get();
			while((seatmask & partmask1) == partmask1){
				if(thisroute.get(i).compareAndSet(seatmask, seatmask & partmask2)){
					ticket.coach = i / seatnum + 1;
					ticket.seat = i % seatnum + 1;
					sold.get(i).set(ticket.tid);
					tc.buyticket(route, departure, arrival, seatmask);
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
		if(!legal(route, departure, arrival))
			return 0;

		int res = tc.inqticket(route, departure, arrival);
		//System.out.println(res);
		return res;
		/*int ans= 0;

		int partmask = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));

		CopyOnWriteArrayList<AtomicInteger>thisroute = routes.get(route - 1);

		//rtLock.readLock().lock();
		for(int i = 0; i < maxnum; i++){
			int seatmask = thisroute.get(i).get();
			if((seatmask & partmask) == partmask){
				ans++;
			}
		}
		//rtLock.readLock().unlock();

		return ans;*/
	}

	@Override
	public boolean refundTicket(Ticket ticket) {

		int route = ticket.route;
		int departure = ticket.departure;
		int arrival = ticket.arrival;

		if(!legal(route, departure, arrival))
			return false;

		int coach = ticket.coach;
		int seat = ticket.seat;

		int loc = (coach - 1) * seatnum + (seat - 1);

		CopyOnWriteArrayList<AtomicLong>sold = solds.get(route - 1);
		if(sold.get(loc).get() != ticket.tid){
			return false;
		}

		int partmask1 = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));
		int partmask2 = stationmask & (~partmask1);

		CopyOnWriteArrayList<AtomicInteger>thisroute = routes.get(route - 1);

		//rtLock.lock();
		int seatmask = thisroute.get(loc).get();
		while((seatmask | partmask2) == partmask2){
			if(thisroute.get(loc).compareAndSet(seatmask, seatmask | partmask1)){
				tc.retticket(route, departure, arrival, seatmask);
				sold.get(loc).set(0);
				return true;
			}
			seatmask = thisroute.get(loc).get();
		}
		//rtLock.unlock();

		return false;
	}

	boolean legal(int route, int departure, int arrival){
		if(route <= 0 || route > routenum || departure >= arrival
			|| departure <= 0 || departure > stationnum 
			|| arrival <= 0 || arrival > stationnum)
			return false;
		return true;
	}

}
