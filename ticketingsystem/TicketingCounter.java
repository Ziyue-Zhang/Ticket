package ticketingsystem;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketingCounter {
    CopyOnWriteArrayList<CopyOnWriteArrayList<AtomicInteger>> routes;

    int routenum;
    int maxnum;
	int stationnum;

    TicketingCounter(int routenum, int maxnum, int stationnum){
        this.routenum = routenum;
        this.maxnum = maxnum;
        this.stationnum = stationnum;

        routes = new CopyOnWriteArrayList<>();
		for(int i = 0; i < routenum; i++) {
			CopyOnWriteArrayList<AtomicInteger>tickets = new CopyOnWriteArrayList<>();

            tickets.add(new AtomicInteger(maxnum));

			for(int j = 0; j <= stationnum; j++){
                for (int k = 0; k < stationnum; k++){
                    tickets.add(new AtomicInteger(maxnum));
                }
            }
			routes.add(tickets);
		}
    }

    void buyticket(int route, int departure, int arrival, int ticket_mask){
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

        CopyOnWriteArrayList<AtomicInteger>thisroute = routes.get(route - 1);

        for(int i = low; i <= departure; i++){
            for (int j = departure+1; j < high; j++){
                thisroute.get(i*stationnum + j).decrementAndGet();
            }
        }

        for(int i = departure+1; i < arrival; i++){
            for (int j = i+1; j < high; j++){
                thisroute.get(i*stationnum + j).decrementAndGet();
            }
        }

    }
    void retticket(int route, int departure, int arrival, int ticket_mask){
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

        CopyOnWriteArrayList<AtomicInteger>thisroute = routes.get(route - 1);

        for(int i = low; i <= departure; i++){
            for (int j = departure+1; j < high; j++){
                thisroute.get(i*stationnum + j).incrementAndGet();
            }
        }

        for(int i = departure+1; i < arrival; i++){
            for (int j = i+1; j < high; j++){
                thisroute.get(i*stationnum + j).incrementAndGet();
            }
        }
    }
    int inqticket(int route, int departure, int arrival){
        CopyOnWriteArrayList<AtomicInteger>thisroute = routes.get(route - 1);
        return thisroute.get(departure*stationnum + arrival).get();
    }
}
