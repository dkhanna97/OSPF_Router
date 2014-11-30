import java.io.*;
import java.net.*;
import java.util.Vector;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/*
OSPF Router - Devruth Khanna
*/

class router{

	static int router_id;
	static InetAddress nse_host;
	static int nse_port;
	static int router_port;
	static DatagramSocket router_socket;
	static byte[] circuit_db;
	static int num_links;
	static Vector<int[]> link_state_DB; 
	static boolean done_receiving_hellos = false;
	static Vector<int[]> neighbors; 
	static int[][] graph; 
	static int lspdu_count_wait = 0;
	static Vector<int[]> routing_table;
	static String file_name;
	static PrintWriter writer;

	router(){
		link_state_DB = new Vector<int[]>();
		neighbors = new Vector<int[]>();
		routing_table = new Vector<int[]>();
		file_name = "router" + String.valueOf(router_id) + ".log";
		try{
			writer = new PrintWriter(file_name, "UTF-8");
		} catch(Exception e) { 
			System.out.println("Could not create file. Abort."); 
			System.exit(-1);
		}
	}
	

	public static void printLinkStateDB() throws Exception{
		writer.println("# Topology Database");
		for(int i = 0; i < link_state_DB.size(); ++i){
			writer.println("Router ID: " + link_state_DB.get(i)[0] + ", LinkID: " + link_state_DB.get(i)[1] + ", Cost: " + 				link_state_DB.get(i)[2]);
		}
	
	}

	public static void printNeighbors() throws Exception{
		writer.println("Printing Neighbor data ... ");
		for(int i = 0; i < neighbors.size(); ++i){
			writer.println("Router ID: " + neighbors.get(i)[0] + ", LinkID: " + neighbors.get(i)[1] );
		}

	}
	public static void sendINIT() throws Exception{

		byte[] sendData = new byte[1024];
		ByteBuffer buff = ByteBuffer.allocate(4);
		buff.order(ByteOrder.LITTLE_ENDIAN);
		buff.putInt(router_id);
		sendData = buff.array();
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, nse_host, nse_port);
		router_socket.send(sendPacket);
	}

	public static void receiveCircuitDB() throws Exception{

		byte[] dbBuff = new byte[1024];	
		DatagramPacket recvPacket = new DatagramPacket(dbBuff, dbBuff.length);
		try{		
			router_socket.receive(recvPacket);
		}catch(Exception e){
			System.out.println("Could not receive packet");
			System.exit(-1);
		}		
		circuit_db = recvPacket.getData();
		ByteBuffer dbBuffer = ByteBuffer.wrap(circuit_db);
		dbBuffer.order(ByteOrder.LITTLE_ENDIAN);
		num_links = dbBuffer.getInt();

		for(int i = 0; i < num_links; ++i){
			int linkID = dbBuffer.getInt();
			int cost = dbBuffer.getInt();
			int[] links = new int[3];
			links[0] = router_id;
			links[1] = linkID;
			links[2] = cost;
			link_state_DB.add(links);
		}
	}

	public static void sendHelloPKT() throws Exception{
	
		for(int i = 0; i < num_links; ++i){
			byte[] sendData = new byte[1024];
			ByteBuffer helloBB = ByteBuffer.allocate(8);
			helloBB.order(ByteOrder.LITTLE_ENDIAN);
			helloBB.putInt(router_id);
			helloBB.putInt(link_state_DB.get(i)[1]);
			byte[] sendData2 = new byte[1024];
			sendData2 = helloBB.array();
			DatagramPacket sendPacket = new DatagramPacket(sendData2, sendData2.length, nse_host, nse_port);
			router_socket.send(sendPacket);
			writer.println("Sending Hello Packet across link" + link_state_DB.get(i)[1]);
		}
		receiveHelloPKT();		
	}
	
	public static void receiveHelloPKT() throws Exception{

			for(int i = 0; i < num_links; ++i){		
				byte[] pktBuff = new byte[1024];			
				DatagramPacket recvPacket = new DatagramPacket(pktBuff, pktBuff.length);
				try{		
					router_socket.receive(recvPacket);

				}catch(Exception e){
					System.out.println("Could not receive hello packet");
					System.exit(-1);
				}
				
				pktBuff = recvPacket.getData();
				ByteBuffer pktBB = ByteBuffer.wrap(pktBuff);
				pktBB.order(ByteOrder.LITTLE_ENDIAN);
				int neighborRouterID = pktBB.getInt();
				int neighborLinkID = pktBB.getInt();
				writer.println("Received hello packet from neighbor R" + neighborRouterID);
				for(int j = 0; j < neighbors.size(); ++j){
					if((neighbors.get(j)[0] == neighborRouterID) && (neighbors.get(j)[1] == neighborLinkID))
						return;
				}
				int[] neighborData = new int[2];
				neighborData[0] = neighborRouterID;
				neighborData[1] = neighborLinkID;
				neighbors.add(neighborData);
			}
	}

	/*Send LSPDU (containing the identifiers and costs of the links to which the router is attached to) to neighbors*/
	public static void sendLSPDUNeighbors() throws Exception{
		for(int i = 0; i < neighbors.size(); ++i){
			for(int j = 0; j < link_state_DB.size(); ++j){
		
				int i_sender = router_id;
				int i_router_id = link_state_DB.get(j)[0];
				int i_link_id = link_state_DB.get(j)[1]; 
				int i_cost = link_state_DB.get(j)[2];
				int i_via = neighbors.get(i)[1];

				sendLSPDU(i_sender, i_router_id, i_link_id, i_cost, i_via);
				addLSPDU(i_sender, i_router_id, i_link_id, i_cost, i_via);

				receiveLSPDU();

			}

		}
		
		while(!isDoneLSPDU()){ 
			receiveLSPDU();
		}

	}
	public static void sendLSPDU(int i_sender, int i_router_id, int i_link_id, int i_cost, int i_via) throws Exception{
		ByteBuffer buff = ByteBuffer.allocate(20);
		buff.order(ByteOrder.LITTLE_ENDIAN);
		buff.putInt(i_sender);
		buff.putInt(i_router_id);
		buff.putInt(i_link_id);
		buff.putInt(i_cost);
		buff.putInt(i_via);
		byte[] sendData = new byte[1024];
		sendData = buff.array();
		try{	
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, nse_host, nse_port);
			router_socket.send(sendPacket);	
			writer.println("Sent LSPDU { "+  String.valueOf(i_sender) + " , " + String.valueOf(i_router_id) + " , " + String.valueOf(i_link_id) + " , " + String.valueOf(i_cost) + "} across link " + String.valueOf(i_via));
		} catch(Exception e){
			System.out.println("Could not send LSPDU. Abort."); 
			System.exit(-1);
		}

	}
	
	public static void addLSPDU(int i_sender, int i_router_id, int i_link_id, int i_cost, int i_via) throws Exception{

		for(int i = 0; i < link_state_DB.size(); ++i){
			if((link_state_DB.get(i)[0] == i_router_id) && (link_state_DB.get(i)[1] == i_link_id))
				return;
		}
		int[] lspdu = new int[3];
		lspdu[0] = i_router_id;
		lspdu[1] = i_link_id;
		lspdu[2] = i_cost;
		link_state_DB.add(lspdu);
	}

	public static void receiveLSPDU() throws Exception{
		byte[] pktBuff = new byte[1024];
		router_socket.setSoTimeout(10000);
		DatagramPacket recvPacket = new DatagramPacket(pktBuff, pktBuff.length);
		try{	
			if(++lspdu_count_wait == 1){	
				System.out.println("Done receiving LSPDU packets. Do not press Ctrl-{D/C}. Will exit shortly...");
			}
			router_socket.receive(recvPacket);
		}catch(Exception e){
			return;
		}
		

		pktBuff = recvPacket.getData();
		ByteBuffer pktBB = ByteBuffer.wrap(pktBuff);
		pktBB.order(ByteOrder.LITTLE_ENDIAN);
		int i_sender = pktBB.getInt();
		int i_router_id = pktBB.getInt();
		int i_link_id = pktBB.getInt();
		int i_cost = pktBB.getInt();
		int i_via = pktBB.getInt();
		writer.println("Received LSPDU { "+  String.valueOf(i_sender) + " , " + String.valueOf(i_router_id) + " , " + String.valueOf(i_link_id)  + " , " + String.valueOf(i_cost) + "} across link " + String.valueOf(i_via));
		addLSPDU(i_sender, i_router_id, i_link_id, i_cost, i_via);

	}

	/* Every link needs to be seen twice in db. That's how we know that we've received all LSPDUs */
	public static boolean isDoneLSPDU() throws Exception{
		for(int i = 0; i < link_state_DB.size(); ++i){
			int cur_link = link_state_DB.get(i)[1];
			boolean flagged = false;
			for(int j = 0; j < link_state_DB.size(); ++j){
				if(i == j) continue;
				if(link_state_DB.get(j)[1] == cur_link) flagged = true; break;
			}
			if(!flagged) return !flagged;
		}
		return true;
	}
	
	public static void sendAgain() throws Exception{
		for(int i =0 ; i < link_state_DB.size(); ++i){
			for(int j = 0; j < neighbors.size(); ++j){
				int i_sender = router_id;
				int i_router_id = link_state_DB.get(i)[0];
				int i_link_id = link_state_DB.get(i)[1];
				int i_cost = link_state_DB.get(i)[2];
				int i_via = neighbors.get(j)[1];
				sendLSPDU(i_sender, i_router_id, i_link_id, i_cost, i_via);
				receiveLSPDU();
			}
		}
	}

	/*Sometimes if a socket is still sending from a previous session, problems can occur. Validate LSDB to ensure there's no out-of-range values. Dispose out-of-range values. */
	public static void validateLSDB() throws Exception{ }

	/*Based on the Link State DB, create a graph */
	public static void createGraph() throws Exception{

		graph = new int[][]{
  				{ 0, 0, 0, 0, 0},
 				{ 0, 0, 0, 0, 0},
  				{ 0, 0, 0, 0, 0},
  				{ 0, 0, 0, 0, 0},
  				{ 0, 0, 0, 0, 0} };
		for(int i = 0; i < link_state_DB.size(); ++i){
			int curLink = link_state_DB.get(i)[1];
			for(int j = 0; j < link_state_DB.size(); ++j){
				if(i == j) continue;
				if(link_state_DB.get(j)[1] == curLink){
					int node1 = link_state_DB.get(i)[0];
					int node2 = link_state_DB.get(j)[0];
					if( (node1 > 5) || (node1 < 1) || (node2 > 5) || (node2 < 1) ){
						System.out.println("Could not create routing graph. Values out of Range. Abort.");
						System.exit(-1);
					}
					graph[node1-1][node2-1] = link_state_DB.get(i)[2];
					graph[node2-1][node1-1] = link_state_DB.get(i)[2];
				}
			}
		}
	}


	/* Run Dijkstra's Algorithm to find shortest distance from starting node to all other nodes (routers) in the network.
	   For each node in the graph, Compute the next hop from starting node. This info (both the shortest distance and the next 		   hop) will go into the routing table.  
	*/
	public static void Dijkstra(int startingNode) throws Exception{

		int origStartingNode = startingNode;
		       
		int[] pred = new int[]{0, 0, 0, 0, 0};
		int[] visited = new int[]{0, 0, 0, 0, 0};
		int[] distance = new int[5];

		int min = 9999999;

		for(int i = 0; i < 5; ++i){
		    for(int j = 0; j < 5; ++j){
			if(i == j) continue;
		        if(graph[i][j] == 0) graph[i][j] = 9999999;
		    }
		}

		distance = graph[startingNode];
		distance[startingNode] = 0;
		visited[startingNode] = 1;

		for(int i = 0; i < 5; ++i){

		    min = 9999999;
		    for(int j = 0; j < 5; ++j){       
		        if((min > distance[j]) && (visited[j] != 1)){
		            min = distance[j];
		            startingNode = j;
		        }
		    }
		    visited[startingNode] = 1;
		    for(int c = 0 ; c < 5; c++){
		        if(visited[c] != 1){
		            if(min + graph[startingNode][c] < distance[c]){
		                distance[c] = min + graph[startingNode][c];
		                pred[c] = startingNode;
		            }
		        }
		    }
		}
		int routTo = 0;
		writer.println("# RIB:");
		for(int i = 0; i < 5; ++i){

			writer.print("R" + router_id + " -> R" + String.valueOf(i+1));
			int j = i;
			routTo = 0;
			if(i == origStartingNode){
				writer.println(" -> Local");
				continue;
			}

			if(i == 0){
				j = pred[i];
			}

			while(j != 0){
				routTo = j;
				j = pred[j];
			}
			
			int[] rtEntry = new int[4];
			rtEntry[0] = router_id;
			rtEntry[1] = i;
			if(i == origStartingNode){
				rtEntry[2] = -1;
			} else{
				rtEntry[2] = routTo;
			}
			rtEntry[3] = distance[i];
			routing_table.add(rtEntry);
	
			writer.println(" -> R" + String.valueOf(routTo + 1) + ", " + distance[i]);
		}
   	}



	public static void main(String[] args) throws Exception{

		if(args.length != 4){
			System.out.println("Invalid usage. See spec for usage details. Abort.");
			System.exit(-1);
		}
		router_id = Integer.parseInt(args[0]);
		nse_host = InetAddress.getByName(args[1]);
		nse_port = Integer.parseInt(args[2]);		
		router_port = Integer.parseInt(args[3]);
		try{
			router_socket = new DatagramSocket(router_port);
		} catch(Exception e){
			System.out.println("Could not create socket for router. Abort.");
			System.exit(-1);
		}
		router r = new router();
		r.sendINIT();
		r.receiveCircuitDB();
		r.sendHelloPKT();
		r.sendLSPDUNeighbors();
		for(int i = 0; i < 5; ++i){
			r.sendAgain();
		}
		r.printLinkStateDB();
		r.createGraph();
		r.Dijkstra(router_id-1);
		router_socket.close();
		writer.close();
	}


};
