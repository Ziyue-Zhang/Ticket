package ticketingsystem;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;

public class TicketingDS implements TicketingSystem {
	int routenum = 5;
	int coachnum = 8;
	int seatnum = 100; 
	int stationnum = 10;
	int threadnum = 16;

	int maxnum;
	long stationmask;
    
	public static AtomicLong count = new AtomicLong(0);
	CopyOnWriteArrayList<CopyOnWriteArrayList<AtomicLong>> data;
    
	ReentrantLock rtLock = new ReentrantLock();
	
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
		
		data = new CopyOnWriteArrayList<>();
		for(int i = 0; i < routenum; i++) {
			CopyOnWriteArrayList<AtomicLong>temp = new CopyOnWriteArrayList<>();
			for(int j = 0; j < maxnum; j++) {
				temp.add(new AtomicLong(stationmask));
			}
			data.add(temp);
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

		CopyOnWriteArrayList<AtomicLong>thisroute = data.get(route - 1);

		//rtLock.lock();
		for(int i = 0; i < maxnum; i++){
			long temp = thisroute.get(i).get();
			while((temp & partmask1) == partmask1){
				if(thisroute.get(i).compareAndSet(temp, temp & partmask2)){
					ticket.coach = i / seatnum + 1;
					ticket.seat = i % seatnum + 1;
					return ticket;
				}
				temp = thisroute.get(i).get();
			}
		}
		//rtLock.unlock();		

		return null;
	}

	@Override
	public int inquiry(int route, int departure, int arrival) {
		int ans= 0;

		long partmask = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));

		CopyOnWriteArrayList<AtomicLong>thisroute = data.get(route - 1);

		//rtLock.readLock().lock();
		for(int i = 0; i < maxnum; i++){
			long temp = thisroute.get(i).get();
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

		long partmask1 = (1 << (stationnum-departure)) - (1 << (stationnum-arrival));
		long partmask2 = stationmask & (~partmask1);

		CopyOnWriteArrayList<AtomicLong>thisroute = data.get(route - 1);

		//rtLock.lock();
		
		long temp = thisroute.get(loc).get();
		while((temp | partmask2) == partmask2){
			if(thisroute.get(loc).compareAndSet(temp, temp | partmask1)){
				return true;
			}
			temp = thisroute.get(loc).get();
		}
		//rtLock.unlock();

		return false;
	}

}
