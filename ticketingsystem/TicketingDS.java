package ticketingsystem;

// import java.util.ArrayList;
import java.util.BitSet;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;


public class TicketingDS implements TicketingSystem {
	private int routeNum = 5;
	private int coachNum = 8;
	private int seatNum = 100;
	private int stationNum = 10;
	private int threadNum = 16;
	private AtomicLong ticketid = new AtomicLong(0);

	private ConcurrentHashMap<Long,Ticket> sold = new ConcurrentHashMap<>(); 
	private BitSet[][][]  coaches;
	private ReentrantLock [][][] coaches_lock;

	// private Map<Integer,Integer> hmap  = new ConcurrentHashMap<Integer,Integer>();
	// private Map<Integer,List<Integer>> id2interval = new HashMap<>();
	// private Map<Long,Boolean> tids = new HashMap<>();

	// private int Id2Val(int routeid,int coachid,int seatid)
	// {
	// 	return 10000*routeid + 100*coachid + seatid;
	// }


	TicketingDS()
	{

	}

	TicketingDS(int routenum,int coachnum,int seatnum,int stationnum,int threadnum)
	{
		this.routeNum = routenum;
		this.coachNum = coachnum;
		this.seatNum = seatnum;
		this.stationNum = seatnum;
		this.threadNum = threadnum;
		// for (int i = 1; i <= routeNum; i++)
		// {
		// 	for (int j = 1; j <= coachNum; j++)
		// 	{
		// 		for (int k = 1; k <= seatNum; k++)
		// 		{
		// 			int key = Id2Val(i, j, k);
		// 			if (id2interval.containsKey(key))
		// 			{
		// 				System.out.println("ID2VAL ERROR!");
		// 			}
		// 			else id2interval.put(key,new ArrayList<>());
		// 		}
		// 	}
		// }

		coaches_lock = new ReentrantLock[routenum][stationnum][coachnum];
		coaches = new BitSet[routenum][stationnum][coachnum];
		for (int i = 0; i < routenum; i++)
		{
			for (int j = 0; j < stationnum; j++)
			{
				for (int k = 0; k < coachnum; k++)
				{
					coaches_lock[i][j][k] = new ReentrantLock();
					coaches[i][j][k] = new BitSet(seatnum);
					coaches[i][j][k].set(0,seatnum);
				}
			}
		}
	}

	public boolean check(int routeid,int departureid,int arrivalid)
	{
		if (routeid <= 0 || routeid > routeNum) return false;
		if (departureid>=arrivalid) return false;
		if (arrivalid>stationNum) return false;
		if (departureid<=0) return false;
		return true;

	}

	public boolean same(Ticket t1,Ticket t2)
	{
		if (t1.tid == t2.tid&&t1.passenger.equals(t2.passenger) && t1.route == t2.route &&t1.departure == t2.departure&&t1.arrival == t2.arrival&&
		t1.coach==t2.coach&&t1.seat == t2.seat) return true;
		return false;
	}

	public Ticket buyTicket(String passenger, int route, int departure, int arrival)
	{
		if (passenger == null || "".equals(passenger)) return null;
		if (!check(route, departure, arrival)) return null;
		int s = departure - 1;
		int t = arrival - 1;
		int route_pos = route - 1;
		boolean find = false;
		int coach_pos = -1;
		int seat_pos = -1;
		BitSet tmp_coach = new BitSet(seatNum); 
		for (int i = 0; i < coachNum; i++)
		{
			coach_pos = i;
			tmp_coach.set(0, seatNum);
			for (int j = s; j < t;j++)
			{
				coaches_lock[route_pos][j][i].lock();
			}
			try{
				for (int j = s; j < t; j++)
				{
					tmp_coach.and(coaches[route_pos][j][i]);
				}
				seat_pos = tmp_coach.nextSetBit(0);
				if (seat_pos!=-1)
				{
					for (int k = s; k < t;k++)
					{
						coaches[route_pos][k][i].set(seat_pos, false);
					}
					find = true;
					break;
				}
			}
			finally{
				for (int j = s; j < t; j++)
				{
					coaches_lock[route_pos][j][i].unlock();
				}
			}
		}
		if (!find) return null;
		Ticket ticket = new Ticket();
		ticket.tid = ticketid.incrementAndGet();
		ticket.passenger = passenger;
		ticket.departure = departure;
		ticket.arrival = arrival;
		ticket.route = route_pos + 1;
		ticket.coach = coach_pos + 1;
		ticket.seat = seat_pos + 1;

		// Ticket ticket_tmp = new Ticket();
		// ticket_tmp.tid = ticket.tid;
		// ticket_tmp.passenger = passenger;
		// ticket_tmp.departure = departure;
		// ticket_tmp.arrival = arrival;
		// ticket_tmp.route = route_pos + 1;
		// ticket_tmp.coach = coach_pos + 1;
		// ticket_tmp.seat = seat_pos + 1;

		sold.put(ticket.tid, ticket);
		return ticket;
	}

	public int inquiry(int route, int departure, int arrival)
	{
		if (!check(route, departure, arrival)) return 0;
		int s = departure - 1;
		int t = arrival - 1;
		int route_pos = route - 1;
		BitSet tmp_coach = new BitSet(seatNum);
		int cnt = 0;
		for (int i = 0; i < coachNum; i++)
		{
			tmp_coach.set(0, seatNum);
			for (int j = s; j < t; j++)
			{
				tmp_coach.and(coaches[route_pos][j][i]);
			}
			cnt += tmp_coach.cardinality();
		}
		return cnt;
	}

	public boolean refundTicket(Ticket ticket)
	{
		if(!sold.containsKey(ticket.tid)) return false;
		Ticket tmp_ticket = sold.get(ticket.tid);
		if (sold.containsKey(ticket.tid))
		{
			if (same(ticket, tmp_ticket))
			{
				int s = ticket.departure - 1;
				int t = ticket.arrival - 1;
				int route_pos = ticket.route - 1;
				int coach_pos = ticket.coach - 1;
				int seat_pos = ticket.seat - 1;
				for (int i = s; i < t; i++)
				{
					coaches_lock[route_pos][i][coach_pos].lock();
				}
				try{
					for (int  i = s; i < t; i++)
					{
						coaches[route_pos][i][coach_pos].set(seat_pos);
					}
				}
				finally{
					for (int i = s; i < t; i++)
					{
						coaches_lock[route_pos][i][coach_pos].unlock();
					}
				}
				sold.remove(ticket.tid);
				return true;
			}
		}
		return false;
	}
}
















	// private boolean intersect(int l1,int r1,int l2,int r2)
	// {
	// 	return (l1<=l2&&l2<r1)||(l2<l1&&r2>l1);
	// }

	// public Ticket buyTicket(String passenger, int route, int departure, int arrival)
	// {
		
	// 	for(int i = 1; i <= coachNum; i++)
	// 	{
	// 		for (int j = 1; j <= seatNum; j++)
	// 		{
	// 			int key = Id2Val(route, i, j);
	// 			List<Integer> intervals = id2interval.get(key);
	// 			for (int y:intervals)
	// 			{
	// 				if(y==0) continue;
	// 				int left = y%10;
	// 				if (left == 0) left = 10;
	// 				int right = (y-left)/10;
	// 				if (intersect(left, right, departure, arrival)) continue;
	// 				Ticket ticket = new Ticket();
	// 				ticket.tid = ticketid.getAndIncrement();
	// 				tids.put(ticket.tid, true);
	// 				ticket.passenger = passenger;
	// 				ticket.route = route;
	// 				ticket.departure = departure;
	// 				ticket.arrival = arrival;
	// 				ticket.coach = i;
	// 				ticket.seat = j;
	// 				intervals.add(10*arrival+departure);
	// 				id2interval.put(key, intervals);
	// 				return ticket;
	// 			}
	// 		}
	// 	}
	// 	return null;
	// }

	// private boolean booked(int left,int right,int routeid,int coachid,int seatid)
	// {    
	// 	return false;
	// }


	// public int inquiry(int route, int departure, int arrival)
	// {
	// 	int cnt = 0;
	// 	for(int i = 1; i <= coachNum; i++)
	// 	{
	// 		for (int j = 1; j <= seatNum; j++)
	// 		{
	// 			int key = Id2Val(route, i, j);
	// 			List<Integer> intervals = id2interval.get(key);
	// 			for (int y:intervals)
	// 			{
	// 				if(y==0) continue;
	// 				int left = y%10;
	// 				if (left == 0) left = 10;
	// 				int right = (y-left)/10;
	// 				if (intersect(left, right, departure, arrival)) continue;
	// 				cnt++;
	// 			}
	// 		}
	// 	}
	// 	return cnt;

	// }

	// public boolean refundTicket(Ticket ticket)
	// {
	// 	long ticketid = ticket.tid;
	// 	if (!tids.containsKey(ticketid)) return false;
	// 	int routeid = ticket.route;
	// 	int departure = ticket.departure;
	// 	int arrival = ticket.arrival;
	// 	for(int i = 1; i <= coachNum; i++)
	// 	{
	// 		for (int j = 1; j <= seatNum; j++)
	// 		{
	// 			int key = Id2Val(routeid, i, j);
	// 			List<Integer> intervals = id2interval.get(key);
	// 			for (int k = 0; k <intervals.size(); k++)
	// 			{
	// 				int y = intervals.get(k);
	// 				if(y==0) continue;
	// 				int left = y%10;
	// 				if (left == 0) left = 10;
	// 				int right = (y-left)/10;
	// 				if (left == departure && right == arrival)
	// 				{
	// 					intervals.remove(k);
	// 					id2interval.put(key,intervals);
	// 					return true;
	// 				}
	// 			}
	// 		}
	// 	}
	// 	return false;
	// }

		
	//ToDo


