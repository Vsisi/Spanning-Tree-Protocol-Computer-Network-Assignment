import java.util.*;
import java.io.*;
import java.lang.management.ThreadInfo;
import java.rmi.server.RemoteRef;
enum PortState{
    BLOCKING, LISTENING, LEARNING, FORWARDING, DISABLED;
}
class Constants{
    public static final int helloTime = 2;
    public static final int forwardDelay = 15;
    public static final int decayTime = helloTime * 7;
    public static final int MAXN = 100000;
    public static String stateTranslator(PortState state) {
        if(state == PortState.BLOCKING) {
            return "BLOCKING";
        }else if(state == PortState.LEARNING) {
            return "LEARNING";
        }else if(state == PortState.LISTENING) {
            return "LISTENING";
        }else if(state == PortState.DISABLED) {
            return "DISABLED";
        }else {
            return "FORWARDING";
        }
    }
    // public final int maxAge = 20;  //we do not expect large network
}
class BPDU {
    public BPDU(int rootID, int srcBridgeID, int srcPortID, int minCost2Root) {
        this.rootID = rootID;
        this.srcBridgeID = srcBridgeID;
        this.srcPortID = srcPortID;
        this.minCost2Root = minCost2Root;
    }
    static public boolean isTheBest(BPDU target, BPDU best) {
        if(target == null) {
            return false;
        }
        if(best == null || target.rootID < best.rootID) {
            return true;
        }else if(target.rootID == best.rootID && target.minCost2Root < best.minCost2Root) {
            return true;
        }else if(target.rootID == best.rootID && target.minCost2Root == best.minCost2Root && target.srcBridgeID < best.srcBridgeID) {
            return true;
        }else if(target.rootID == best.rootID && target.minCost2Root == best.minCost2Root && target.srcBridgeID == best.srcBridgeID && target.srcPortID < best.srcPortID) {
            return true;
        }else if(target.rootID == best.rootID && target.minCost2Root == best.minCost2Root && target.srcBridgeID == best.srcBridgeID && target.srcPortID == best.srcPortID) {
            return true;
        }
        return false;
    }
    int rootID;
    int srcBridgeID;
    int srcPortID;
    int minCost2Root;
}
class Port{
    public Port(int id, PortState state, int lanID, int cost) {
        this.id = id;
        this.state = PortState.FORWARDING;
        this.lanID = lanID;
        this.cost = cost;
    }
    int id; //this may be equal
    PortState state;
    int lanID;
    int cost;
}
class LAN{
    public LAN(int id) {
        this.id = id;
    }
    public void broadcast2All(BPDU bpdu, int curBridgeID, int curPortID) {
        for(Integer bridgeID:connBridges.keySet()) {
            Bridge bridge = idTable.getBridge(bridgeID);
            for(Integer portID:connBridges.get(bridgeID)) {
                if(curBridgeID == bridgeID && curPortID == portID) {
                    continue; //broadcast to all other port in this lan
                }
                Port port = bridge.ports.get(portID);
                bridge.receiveBPDU(new BPDU(bpdu.rootID, bpdu.srcBridgeID, bpdu.srcPortID, bpdu.minCost2Root), port);
            }
        }
    }
    int id;
    Map<Integer, Set<Integer>> connBridges = new HashMap<Integer, Set<Integer>>();
}
class Bridge{
    public Bridge(int id) {
        this.id = id;
        this.rootID = id; //at first bridge thinks itself is root bridge
        this.minCost2Root = 0; //at first min cost to root is zero
        globalBestBPDU = new BPDU(id, Constants.MAXN, Constants.MAXN, 0);
        this.rootPortID = -1;
    }
    public void broadcast() {
        for(Integer srcPortID:ports.keySet()) { //for every port in this bridge
            Port srcPort = ports.get(srcPortID); //get every port in this bridge
            int lanID = srcPort.lanID; //get LAN connected to this port
            BPDU bpdu = new BPDU(this.rootID, this.id, srcPortID, this.minCost2Root); //init BPDU: broadcast the root id sender, senders id, senders port, and min cost to root(we only consider the costof receive port)
            idTable.getLAN(lanID).broadcast2All(bpdu, this.id, srcPortID); //broadcast to all port conn in this LAN
        }
    }
    public void receiveBPDU(BPDU bpdu, Port receivePort) {
        if(buffer.get(receivePort) == null) {
            buffer.put(receivePort, new HashSet<BPDU>());
            buffer.get(receivePort).add(bpdu);
        }else {
            buffer.get(receivePort).add(bpdu);
        }
    }
    public void update() {
        do {
            HashMap<Port, HashSet<BPDU>> temp = new HashMap<Port, HashSet<BPDU>>();
            temp.putAll(buffer);
            buffer.clear();
            // Scanner scan = new Scanner(System.in);
            for(Integer receivePortID:ports.keySet()) {
                Port receivePort = ports.get(receivePortID);
                if(temp.get(receivePort) == null) {
                    continue;
                }
                for(BPDU bpdu:temp.get(receivePort)) {    //find best configuration info
                    // System.out.println(id + " " + bpdu.srcBridgeID + " " + bpdu.minCost2Root + " " + receivePort.id);
                    // scan.nextLine();
                    if(receivePort.id == this.rootPortID && BPDU.isTheBest(bpdu, theBestOne.get(receivePort))) { //communicate with root bridge
                        for(Integer portID:ports.keySet()) { //resend
                            if(portID != receivePort.id && ports.get(portID).state != PortState.BLOCKING) { //resend to all other ports
                                BPDU resendBPDU = new BPDU(bpdu.rootID, this.id, portID, bpdu.minCost2Root + receivePort.cost);
                                idTable.getLAN(ports.get(portID).lanID).broadcast2All(resendBPDU, this.id, receivePort.id);
                            }
                        }
                    }
    
                    if(this.id == bpdu.srcBridgeID && receivePort.id == bpdu.srcPortID) {
                        continue;
                    }
    
                    if(BPDU.isTheBest(bpdu, new BPDU(globalBestBPDU.rootID, this.id, receivePort.id, globalBestBPDU.minCost2Root))) { // receive better bpdu from a lan
                        receivePort.state = PortState.BLOCKING;
                        designated.put(idTable.getLAN(receivePort.lanID), false);
                    }
    
                    if(BPDU.isTheBest(bpdu, theBestOne.get(receivePort))) {
                        theBestOne.put(receivePort, new BPDU(bpdu.rootID, bpdu.srcBridgeID, bpdu.srcPortID, bpdu.minCost2Root));
                    }
    
                    if(BPDU.isTheBest(new BPDU(bpdu.rootID, bpdu.srcBridgeID, bpdu.srcPortID, bpdu.minCost2Root + receivePort.cost), globalBestBPDU)) {
                        globalBestBPDU = new BPDU(bpdu.rootID, bpdu.srcBridgeID, bpdu.srcPortID, bpdu.minCost2Root + receivePort.cost);
                        
                        if(this.rootID == bpdu.rootID && this.minCost2Root == bpdu.minCost2Root + receivePort.cost) {
                            continue;
                        }

                        this.rootID = bpdu.rootID; 
                        this.minCost2Root = bpdu.minCost2Root + receivePort.cost;


                        for(Integer portID:ports.keySet()) { //resend
                            if(ports.get(portID).state == PortState.BLOCKING) {
                                ports.get(portID).state = PortState.FORWARDING;
                                designated.put(idTable.getLAN(ports.get(portID).lanID), true);
                            }
                            if(portID != receivePort.id && ports.get(portID).state != PortState.BLOCKING) { //resend to all other ports
                                BPDU resendBPDU = new BPDU(this.rootID, this.id, portID, bpdu.minCost2Root + receivePort.cost);
                                idTable.getLAN(ports.get(portID).lanID).broadcast2All(resendBPDU, this.id, receivePort.id);
                            }
                        }
                    }
                    determineRootPort();
                }
            }
        }while(!buffer.isEmpty());
    }
    public void determineRootPort() {
        if(this.id == this.rootID) {
            return;
        }
        BPDU bestBPDU = new BPDU(Constants.MAXN, Constants.MAXN, Constants.MAXN, Constants.MAXN);
        for(Integer portID:this.ports.keySet()) {
            Port port = ports.get(portID);
            BPDU bpdu = theBestOne.get(port);
            if(bpdu == null) {
                continue;
            }
            if(BPDU.isTheBest(new BPDU(bpdu.rootID, bpdu.srcBridgeID, bpdu.srcPortID, bpdu.minCost2Root + port.cost), bestBPDU)) {
                bestBPDU = new BPDU(bpdu.rootID, bpdu.srcBridgeID, bpdu.srcPortID, bpdu.minCost2Root + port.cost);
                this.rootPortID = portID;
            }
        }
    }
    int id;
    int rootID;
    int minCost2Root;
    int rootPortID;
    HashMap<LAN, Boolean> designated = new HashMap<LAN, Boolean>();
    HashMap<Port, HashSet<BPDU>> buffer = new HashMap<Port, HashSet<BPDU>>();
    HashMap<Port, BPDU> theBestOne = new HashMap<Port, BPDU>();
    BPDU globalBestBPDU;
    HashMap<Integer, Port> ports = new HashMap<Integer, Port>();
}
class idTable{
    static public void addNewLAN(Integer lanID, LAN lan) {
        id2LAN.put(lanID, lan);
    }
    static public void addNewBridge(Integer bridgeID, Bridge bridge) {
        id2Bridge.put(bridgeID, bridge);
    }
    static public LAN getLAN(Integer lanID) {
        return id2LAN.get(lanID);
    }
    static public Bridge getBridge(Integer bridgeID) {
        return id2Bridge.get(bridgeID);
    }
    static public Set<Integer> getBridgeKeySet() {
        return id2Bridge.keySet();
    }
    static public Set<Integer> getLANKeySet() {
        return id2LAN.keySet();
    }
    static private HashMap<Integer, LAN> id2LAN = new HashMap<Integer, LAN>();
    static private HashMap<Integer, Bridge> id2Bridge = new HashMap<Integer, Bridge>();
}
class stpProcess {
    public void constructNetwork(int bridgeID, int portID, int lanID, int portCost) { //accept input to create a network
            if(idTable.getBridge(bridgeID) != null) { //old bridge
            Bridge bridge = idTable.getBridge(bridgeID);

            if(bridge.ports.keySet().contains(portID)) { // old port 
                //discard and do nothing, one port of one bridge can't connect to several LANs
            }else { //new port
                Port newPort = new Port(portID, PortState.LISTENING, lanID, portCost);
                bridge.theBestOne.put(newPort, new BPDU(bridge.id, bridge.id, portID, 0));
                bridge.ports.put(portID, newPort); // add a new port, set state to LISTENING
                if(idTable.getLAN(lanID) != null) { //old lan
                    if(idTable.getLAN(lanID).connBridges.get(bridgeID) == null) { //new bridge to this lan
                        idTable.getLAN(lanID).connBridges.put(bridgeID, new HashSet<Integer>());
                    }
                    idTable.getLAN(lanID).connBridges.get(bridgeID).add(portID); //connect to this LAN
                }else { //new lan
                    LAN newLAN = new LAN(lanID); //create new lan
                    newLAN.connBridges.put(bridgeID, new HashSet<Integer>());
                    newLAN.connBridges.get(bridgeID).add(portID); //connect to this LAN
                    idTable.addNewLAN(lanID, newLAN);  //add new lan
                }
            }
        }else { //new bridge
            Bridge newBridge = new Bridge(bridgeID); //create a new bridge
            Port newPort = new Port(portID, PortState.LISTENING, lanID, portCost);
            newBridge.theBestOne.put(newPort, new BPDU(newBridge.id, newBridge.id, portID, 0));
            newBridge.ports.put(portID, newPort); //add a new prot to the new bridge
            if(idTable.getLAN(lanID) != null) { //old lan
                idTable.getLAN(lanID).connBridges.put(bridgeID, new HashSet<Integer>()); //new bridge so directly create 
                idTable.getLAN(lanID).connBridges.get(bridgeID).add(portID); //connect to this LAN
            }else { //new lan
                LAN newLAN = new LAN(lanID); //create new lan
                newLAN.connBridges.put(bridgeID, new HashSet<Integer>());
                newLAN.connBridges.get(bridgeID).add(portID); //connect to this LAN
                idTable.addNewLAN(lanID, newLAN);  //add new lan
            }
            idTable.addNewBridge(bridgeID, newBridge); // add a new bridge
        }
    }
    public void broadcastBPDU() {
        for(Integer bridgeID:idTable.getBridgeKeySet()) {
            Bridge bridge = idTable.getBridge(bridgeID);
            if(bridge.id == bridge.rootID) { //only 'root' are allowed to broadcast
                bridge.broadcast();
            }
        }
    }
    public void determineRoot() {
        broadcastBPDU();
        processBPDU();
    }
    public void showRootPort() {
        System.out.println("Root Port: ");
        for(Integer bridgeID:idTable.getBridgeKeySet()) {
            Bridge bridge = idTable.getBridge(bridgeID);
            if(bridge.rootPortID != -1) {
                bridge.ports.get(bridge.rootPortID).state = PortState.FORWARDING;//set root port's state to forwarding
            }
            System.out.println("Bridge " + bridgeID + " has the root port " + bridge.rootPortID);
        }
        System.out.println();
    }
    public void determineDesignatedBridge() {
        System.out.println("Designated Bridge: ");
        for(Integer lanID:idTable.getLANKeySet()) {
            LAN lan = idTable.getLAN(lanID);
            for(Integer connBridgeID:lan.connBridges.keySet()) {
                Bridge bridge = idTable.getBridge(connBridgeID);
                if(bridge.designated.get(lan) == null || bridge.designated.get(lan) == true) { //is designated bridge
                    int designatedPortID = Constants.MAXN;
                    for(Integer portID:bridge.ports.keySet()) {
                        if(bridge.ports.get(portID).lanID == lanID && portID < designatedPortID) {//is designated port
                            designatedPortID = portID;
                        }
                    }
                    System.out.println("LAN" + lanID + ": " + connBridgeID);
                }
            }
        }
        System.out.println();
    }
    public void determineDesignatedPorts() {
        System.out.println("Designated Ports: ");
        for(Integer lanID:idTable.getLANKeySet()) {
            LAN lan = idTable.getLAN(lanID);
            for(Integer connBridgeID:lan.connBridges.keySet()) {
                Bridge bridge = idTable.getBridge(connBridgeID);
                if(bridge.designated.get(lan) == null || bridge.designated.get(lan) == true) { //is designated bridge
                    int designatedPortID = Constants.MAXN;
                    for(Integer portID:bridge.ports.keySet()) {
                        if(bridge.ports.get(portID).lanID == lanID && portID < designatedPortID) {//is designated port
                            designatedPortID = portID;
                        }
                    }
                    bridge.ports.get(designatedPortID).state = PortState.FORWARDING; //set states of designated ports to forwarding
                    // System.out.println("Port " + designatedPortID + " is designated port of bridge " + connBridgeID);
                    System.out.println("Bridge " + connBridgeID + " has designated port " + designatedPortID);
                }
            }
        }
        System.out.println();
    }
    public void processBPDU() {
        boolean converge = true;
        do {
            converge = true;
            for(Integer bridgeID:idTable.getBridgeKeySet()) {
                Bridge bridge = idTable.getBridge(bridgeID);
                if(!bridge.buffer.isEmpty()) {
                    converge = false;
                }
                bridge.update();
            }
        }while(!converge);
    }
    public void outputPortState() {
        for(Integer bridgeID:idTable.getBridgeKeySet()) {
            Bridge bridge = idTable.getBridge(bridgeID);
            System.out.println("Bridge " + bridgeID + ":");
            for(Integer portID:bridge.ports.keySet()) {
                Port port = bridge.ports.get(portID);
                System.out.println(portID + " " + Constants.stateTranslator(port.state));
            }
            System.out.println();
        }
    }
    public void outputResult() {
        System.out.println("Min cost to root: ");
        int sum = 0;
        for(Integer bridgeID:idTable.getBridgeKeySet()) {
            Bridge bridge = idTable.getBridge(bridgeID);
            sum += bridge.minCost2Root;
            System.out.println("Bridge" + bridgeID + ": " + bridge.minCost2Root);
        }
        System.out.println(sum);
        System.out.println();
    }
}
public class stp1{
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(new FileReader("data.txt"));
        stpProcess stpP = new stpProcess();
        
        int n = scanner.nextInt();
        for(int i = 0; i < n; i++) {
            int bridgeID = scanner.nextInt();
            int portID = scanner.nextInt();
            int lanID = scanner.nextInt();
            int portCost = scanner.nextInt();
            stpP.constructNetwork(bridgeID, portID, lanID, portCost);
        }


        for(int i = 0; i < 10; i++) {
            stpP.determineRoot();
        }
        stpP.showRootPort();
        stpP.determineDesignatedBridge();
        stpP.determineDesignatedPorts();
        stpP.outputPortState();
        stpP.outputResult();
    }
}