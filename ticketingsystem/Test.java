package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {
	final static int threadnum = 4;
	final static int routenum = 3; // route is designed from 1 to 3
	final static int coachnum = 5; // coach is arranged from 1 to 5
	final static int seatnum = 10; // seat is allocated from 1 to 20
	final static int stationnum = 8; // station is designed from 1 to 5

	final static int testnum = 10000000;
	final static int retpc = 10; // return ticket operation is 10% percent
	final static int buypc = 40; // buy ticket operation is 30% percent
	final static int inqpc = 100; //inquiry ticket operation is 60% percent

	private final static long[] retstart = new long[threadnum];
	private final static long[] buystart = new long[threadnum];
	private final static long[] inqstart = new long[threadnum];

	private final static long[] rettime = new long[threadnum];
	private final static long[] buytime = new long[threadnum];
	private final static long[] inqtime = new long[threadnum];


	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid; 
	}

	public static void main(String[] args) throws InterruptedException {
        
		Thread[] threads = new Thread[threadnum];
		
		final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
	    
		for (int i = 0; i< threadnum; i++) {
	    	threads[i] = new Thread(new Runnable() {
                public void run() {
            		Random rand = new Random();
                	Ticket ticket = new Ticket();
            		ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();
            		
             		for (int i = 0; i < testnum; i++) {
            			int sel = rand.nextInt(inqpc);
            			if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
            				int select = rand.nextInt(soldTicket.size());
           				if ((ticket = soldTicket.remove(select)) != null) {
								retstart[ThreadId.get()] = System.nanoTime();
            					if (tds.refundTicket(ticket)) {
									rettime[ThreadId.get()] += System.nanoTime() - retstart[ThreadId.get()];
            						//System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach  + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
            						//System.out.flush();
            					} else {
            						//System.out.println(preTime + " " + String.valueOf(System.nanoTime()-startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
            						//System.out.flush();
            					}
            				} else {
								//long preTime = System.nanoTime() - startTime;
            					//System.out.println(preTime + " " + String.valueOf(System.nanoTime()-startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
        						//System.out.flush();
            				}
            			} else if (retpc <= sel && sel < buypc) { // buy ticket
            				String passenger = passengerName();
            				int route = rand.nextInt(routenum) + 1;
            				int departure = rand.nextInt(stationnum - 1) + 1;
            				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
							buystart[ThreadId.get()] = System.nanoTime();
            				if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
								buytime[ThreadId.get()] += System.nanoTime() - buystart[ThreadId.get()];
            					//System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
            					soldTicket.add(ticket);
        						//System.out.flush();
            				} else {
            					//System.out.println(preTime + " " + String.valueOf(System.nanoTime()-startTime) + " " + ThreadId.get() + " " + "TicketSoldOut" + " " + route + " " + departure+ " " + arrival);
        						//System.out.flush();
            				}
            			} else if (buypc <= sel && sel < inqpc) { // inquiry ticket
            				
            				int route = rand.nextInt(routenum) + 1;
            				int departure = rand.nextInt(stationnum - 1) + 1;
            				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
							inqstart[ThreadId.get()] = System.nanoTime();
            				int leftTicket = tds.inquiry(route, departure, arrival);
							inqtime[ThreadId.get()] += System.nanoTime() - inqstart[ThreadId.get()];
            				//System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "RemainTicket" + " " + leftTicket + " " + route+ " " + departure+ " " + arrival);
    						//System.out.flush();  
    						         			
            			}
            		}

                }
            });
              threads[i].start();
 	    }
	
	    for (int i = 0; i< threadnum; i++) {
	    	threads[i].join();
	    }	
		long tottime = 0;
		for(int i = 0; i < threadnum; i++){
			long tmptime = 0;
			tmptime += rettime[i];
			tmptime += buytime[i];
			tmptime += inqtime[i];
			if(tottime < tmptime){
				tottime = tmptime;
			}
		}
		double finishTime = tottime / 1000000;
		double res = testnum * threadnum / finishTime;
		System.out.println(res);	
	}
}
