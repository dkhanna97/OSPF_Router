OSPF_Router
===========

By Devruth Khanna

This router software implements the OSPF (Open Shortest Path First) protocol to discover the shortest path from one router to all other routers in the network. By default, the software assumes that there are a total of five different routers in the network. 

To install the software, type make.

On one host, run the emulator program, to which all the routers communicate with. And another host, run 5 different instances of the program in 5 different terminals. All routers are to be running on the same host.

Running the emulator: ./nse-linux386 <routers_host> <nse_port>     
Running the router: java router <router_id> <nse_host> <nse_port> <router_port>


Example usage:

On host ubuntu1204-002.student.cs.uwaterloo.ca, run:
	./nse-linux386 nettop53 7999

On nettop53.student.cs.uwaterloo.ca, run:
	java router 1 ubuntu1204-002 7999 7990
	java router 2 ubuntu1204-002 7999 7992
	java router 3 ubuntu1204-002 7999 7993
	java router 4 ubuntu1204-002 7999 7994
	java router 5 ubuntu1204-002 7999 7995

There are no scripts to generate the program. Simply run the above 6 commands to execute the program.
5 log files will be created: {router1, router2, router3, router4, router5}.log 
In each log file, all communication amongst the routers is logged.
The final routing table and a complete topology of the network, including costs between routers is also written to the log files.

Version of javac: 1.7.0_51

