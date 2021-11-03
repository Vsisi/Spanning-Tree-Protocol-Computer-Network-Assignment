import java.util.*;

import javax.naming.CannotProceedException;
import javax.swing.text.html.HTMLDocument.BlockElement;

import java.io.*;
import java.net.http.HttpResponse.BodyHandler;
enum PortState{
    BLOCKING, LISTENING, LEARNING, FORWARDING, DISABLED;
}
class Constants{
    public static final int helloTime = 2;
    public static final int forwardDelay = 15;
    public static final int timeOut = helloTime * 7;
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
    int rootID;
    int srcBridgeID;
    int srcPortID;
    int minCost2Root;
}
class Port{
    public Port(int id, PortState state, int lanID, int cost) {
        this.id = id;
        this.state = PortState.LISTENING;
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
                bridge.receiveBPDU(bpdu, port);
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
        buffer.put(bpdu, receivePort); //add bpdu received and which port it comes from
    }
    public void update() {
        do {
            HashMap<BPDU, Port> temp = new HashMap<BPDU, Port>();
            temp.putAll(buffer);
            buffer.clear();
            // Scanner scan = new Scanner(System.in);
            for(BPDU bpdu:temp.keySet()) {    //find best configuration info
                Port receivePort = temp.get(bpdu);
                // System.out.println(id + " " + bpdu.srcBridgeID + " " + bpdu.minCost2Root);
                // scan.nextLine();
                if(receivePort.id == this.rootPortID && this.rootID == bpdu.rootID && this.minCost2Root > bpdu.minCost2Root) {
                    for(Integer portID:ports.keySet()) { //resend
                        if(portID != receivePort.id) { //resend to all other ports
                            BPDU resendBPDU = new BPDU(this.rootID, this.id, portID, this.minCost2Root);
                            idTable.getLAN(ports.get(portID).lanID).broadcast2All(resendBPDU, this.id, receivePort.id);
                        }
                    }
                }
                if(this.rootID < bpdu.rootID) {
                    //discard, do nothing
                }else if(this.rootID > bpdu.rootID) {
                    //update bridge info
                    this.rootID = bpdu.rootID; 
                    this.minCost2Root = bpdu.minCost2Root + receivePort.cost;

                    designated.clear();
                    designated.put(idTable.getLAN(receivePort.lanID), false);
                    for(Integer portID:ports.keySet()) { //resend
                        if(portID != receivePort.id) { //resend to all other ports
                            BPDU resendBPDU = new BPDU(this.rootID, this.id, portID, this.minCost2Root);
                            idTable.getLAN(ports.get(portID).lanID).broadcast2All(resendBPDU, this.id, receivePort.id);
                        }
                    }
                }else { //rootID equals, COMPARE cost to root(elect designated bridge)
                    if(this.minCost2Root < bpdu.minCost2Root) {
                        //discard, do nothing
                    }else if(this.minCost2Root > bpdu.minCost2Root) {
                        this.minCost2Root = Math.min(bpdu.minCost2Root + receivePort.cost, this.minCost2Root);

                        designated.put(idTable.getLAN(receivePort.lanID), false);

                        for(Integer portID:ports.keySet()) { //resend
                            if(portID != receivePort.id) { //resend to all other ports
                                BPDU resendBPDU = new BPDU(this.rootID, this.id, portID, bpdu.minCost2Root + receivePort.cost);
                                idTable.getLAN(ports.get(portID).lanID).broadcast2All(resendBPDU, this.id, receivePort.id);
                            }
                        }
                    }else {
                        if(this.id < bpdu.srcBridgeID) {

                        }else if(this.id > bpdu.srcBridgeID) {
                            designated.put(idTable.getLAN(receivePort.lanID), false);
                        }else {
                            
                        }
                    }
                    
                }
    
                if(bufferAll.get(receivePort) == null) {
                    bufferAll.put(receivePort, new HashSet<BPDU>());
                }
                bufferAll.get(receivePort).add(new BPDU(bpdu.rootID, bpdu.srcBridgeID, bpdu.srcPortID, bpdu.minCost2Root + receivePort.cost));

                determineRootPort();
            }
        }while(!buffer.isEmpty());
    }
    public void determineRootPort() {
        if(this.id == this.rootID) {
            return;
        }
        int minPortCost2Root = Constants.MAXN;
        for(Port port:this.bufferAll.keySet()) {
            for(BPDU bpdu:this.bufferAll.get(port)) {
                if(bpdu.rootID == this.rootID) {
                    if(bpdu.minCost2Root < minPortCost2Root) { //compare cost to root
                        this.rootPortID = port.id;
                        minPortCost2Root = bpdu.minCost2Root;
                    }else if(bpdu.minCost2Root == minPortCost2Root && port.id < this.rootPortID) { //equal then compare bridge id
                        this.rootPortID = port.id;
                    }
                }
            }
        }
    }
    int id;
    int rootID;
    int minCost2Root;
    int rootPortID;
    HashMap<LAN, Boolean> designated = new HashMap<LAN, Boolean>();
    HashMap<BPDU, Port> buffer = new HashMap<BPDU, Port>();
    HashMap<Port, HashSet<BPDU>> bufferAll = new HashMap<Port, HashSet<BPDU>>();
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
            System.out.println(bridgeID + " " + idTable.getBridge(bridgeID).rootPortID);
        }
        System.out.println();
    }
    public void determineDesignatedBridge() {
        System.out.println("Designated Bridge: ");
        for(Integer lanID:idTable.getLANKeySet()) {
            LAN lan = idTable.getLAN(lanID);
            for(Integer connBridgeID:lan.connBridges.keySet()) {
                Bridge bridge = idTable.getBridge(connBridgeID);
                if(bridge.designated.get(lan) == null) { //is designated bridge
                    System.out.println(lanID + " " + connBridgeID);
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
public class stp{
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
        //here we need to accept input

        stpP.determineRoot();
        stpP.showRootPort();
        stpP.determineDesignatedBridge();
        stpP.outputResult();
    }
}