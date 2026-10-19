package com.maher.kaitv32;

import android.Manifest;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.DhcpInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.LinkAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.net.SocketAddress;
import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.util.concurrent.ConcurrentHashMap;
import java.net.Socket;
import android.content.pm.PackageManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@CapacitorPlugin(name="KaitLanHost")
public class KaitLanHostPlugin extends Plugin {
  private volatile LocalServer server;
  // Native WebSocket clients avoid mixed-content restrictions in the HTTPS-origin Capacitor WebView.
  private final Map<String, WebSocketClient> clients = new ConcurrentHashMap<>();
  private void clientEvent(String id,String event,String info){
    JSObject payload=new JSObject();payload.put("id",id);payload.put("event",event);
    if(info!=null)payload.put("data",info);
    notifyListeners("kaitSocketEvent",payload);
  }
  @PluginMethod public void connectClient(PluginCall call){
    final String id=call.getString("id"),url=call.getString("url");
    if(id==null||!id.matches("[A-Za-z0-9_-]{1,80}")||url==null){call.reject("Missing socket id or URL");return;}
    try{
      URI uri=new URI(url);
      if(!"ws".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getPort()!=10000){call.reject("Nearby connections require ws://host:10000");return;}
      WebSocketClient previous=clients.remove(id);if(previous!=null)previous.close();
      WebSocketClient client=new WebSocketClient(uri){
        @Override public void onOpen(ServerHandshake handshake){clientEvent(id,"open",null);}
        @Override public void onMessage(String message){clientEvent(id,"message",message);}
        @Override public void onClose(int code,String reason,boolean remote){clients.remove(id,this);clientEvent(id,"close",code+" "+reason);}
        @Override public void onError(Exception error){clientEvent(id,"error",error==null?"Unknown error":String.valueOf(error.getMessage()));}
      };
      clients.put(id,client);client.connect();call.resolve();
    }catch(Exception e){call.reject("Native socket could not start: "+e.getMessage());}
  }
  @PluginMethod public void sendClient(PluginCall call){
    String id=call.getString("id"),payload=call.getString("data");WebSocketClient c=clients.get(id);
    if(c==null||!c.isOpen()){call.reject("Native socket not connected");return;}
    try{c.send(payload==null?"":payload);call.resolve();}catch(Exception e){call.reject(e.getMessage());}
  }
  @PluginMethod public void closeClient(PluginCall call){
    String id=call.getString("id");WebSocketClient c=clients.remove(id);
    if(c!=null)try{c.close();}catch(Exception ignored){}call.resolve();
  }
  // Query room advertisements entirely in native code, not WebView ws://.
  @PluginMethod public void probeRooms(PluginCall call){
    final String ip=call.getString("ip");
    if(ip==null||!ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")){call.reject("Invalid local address");return;}
    new Thread(()->{
      final CountDownLatch done=new CountDownLatch(1);
      final String[] response={""};
      try{
        WebSocketClient client=new WebSocketClient(new URI("ws://"+ip+":10000/ws")){
          @Override public void onOpen(ServerHandshake h){send("{\"type\":\"list\"}");}
          @Override public void onMessage(String m){response[0]=m;done.countDown();}
          @Override public void onClose(int c,String r,boolean remote){done.countDown();}
          @Override public void onError(Exception e){done.countDown();}
        };
        client.connect();done.await(3,TimeUnit.SECONDS);client.close();
      }catch(Exception ignored){}
      JSObject result=new JSObject();result.put("response",response[0]);
      getActivity().runOnUiThread(()->call.resolve(result));
    },"kait-probe").start();
  }

  @PluginMethod public void startHost(PluginCall call) {
    try {
      if(server==null){ server=new LocalServer(10000); server.start(); if(!server.waitForStart()){server=null;call.reject("Nearby room server did not start. Retry.");return;} }
      JSObject out=new JSObject();out.put("port",10000);out.put("addresses",getIPs()); call.resolve(out);
    }catch(Exception e){server=null;call.reject("Could not start local server: "+e.getMessage());}
  }
  @PluginMethod public void getAddresses(PluginCall call){ JSObject o=new JSObject();o.put("addresses",getIPs());call.resolve(o); }
  @PluginMethod public void stopHost(PluginCall call){try{if(server!=null){server.stop(1000);server=null;} call.resolve();}catch(Exception e){call.reject(e.getMessage());}}
  // Guest's Wi-Fi DHCP gateway is typically the hotspot host; not the guest's own IP.
  @PluginMethod public void discoveryAddresses(PluginCall call){
    org.json.JSONArray result=new org.json.JSONArray();java.util.LinkedHashSet<String> ips=new java.util.LinkedHashSet<>();
    try{
      WifiManager wifi=(WifiManager)getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      DhcpInfo dhcp=wifi.getDhcpInfo();
      if(dhcp!=null&&dhcp.gateway!=0){int g=dhcp.gateway;String gw=(g&255)+"."+((g>>8)&255)+"."+((g>>16)&255)+"."+((g>>24)&255);ips.add(gw);}
    }catch(Exception ignored){}
    try{
      Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();
      while(nets.hasMoreElements()){
        NetworkInterface net=nets.nextElement();if(!net.isUp()||net.isLoopback()||!isWifi(net.getName()))continue;
        Enumeration<InetAddress> all=net.getInetAddresses();while(all.hasMoreElements()){
          InetAddress addr=all.nextElement();if(!(addr instanceof Inet4Address)||!addr.isSiteLocalAddress())continue;
          String ip=addr.getHostAddress();ips.add(ip);
          String[] oct=ip.split("\\.");if(oct.length==4){String prefix=oct[0]+"."+oct[1]+"."+oct[2]+".";ips.add(prefix+"1");}
        }
      }
    }catch(Exception ignored){}
    for(String ip:ips)result.put(ip);
    JSObject ret=new JSObject();ret.put("addresses",result);call.resolve(ret);
  }
  // Scan over the actual Android Wi-Fi network, not the cellular default route.
  // The port probe runs in background threads to avoid blocking the Capacitor UI.
  @PluginMethod public void findHosts(PluginCall call){
    new Thread(() -> {
      LinkedHashSet<String> ips=new LinkedHashSet<>();
      LinkedHashSet<String> gateways=new LinkedHashSet<>();
      Network wifiNetwork=null;
      try{
        ConnectivityManager cm=(ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        for(Network net:cm.getAllNetworks()){
          NetworkCapabilities caps=cm.getNetworkCapabilities(net);
          if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
          LinkProperties lp=cm.getLinkProperties(net);
          if(lp==null)continue;
          wifiNetwork=net;
          for(RouteInfo route:lp.getRoutes()){
            InetAddress gateway=route.getGateway();
            if(gateway instanceof Inet4Address&&!gateway.isAnyLocalAddress())gateways.add(gateway.getHostAddress());
          }
          for(LinkAddress link:lp.getLinkAddresses()){
            InetAddress addr=link.getAddress();
            if(!(addr instanceof Inet4Address)||!addr.isSiteLocalAddress())continue;
            String ip=addr.getHostAddress();String[] a=ip.split("\\.");
            if(a.length!=4)continue;
            String prefix=a[0]+"."+a[1]+"."+a[2]+".";
            // Most Android hotspot networks use a /24 subnet. Probe gateways first.
            for(int k=1;k<=254;k++)if(!ip.equals(prefix+k))ips.add(prefix+k);
          }
        }
      }catch(Exception ignored){}
      try{
        WifiManager wifi=(WifiManager)getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp=wifi.getDhcpInfo();
        if(dhcp!=null&&dhcp.gateway!=0){int g=dhcp.gateway;gateways.add((g&255)+"."+((g>>8)&255)+"."+((g>>16)&255)+"."+((g>>24)&255));}
      }catch(Exception ignored){}
      LinkedHashSet<String> candidates=new LinkedHashSet<>(gateways);
      candidates.addAll(ips);
      // A hotspot can have a nonstandard gateway; include common fallbacks.
      for(String fallback:new String[]{"192.168.43.1","192.168.1.1","192.168.0.1","192.168.137.1","172.20.10.1"})candidates.add(fallback);
      final Network activeWifi=wifiNetwork;
      ExecutorService pool=Executors.newFixedThreadPool(32);
      java.util.concurrent.ConcurrentLinkedQueue<String> found=new java.util.concurrent.ConcurrentLinkedQueue<>();
      List<Future<?>> tasks=new ArrayList<>();
      for(String ip:candidates){
        tasks.add(pool.submit(() -> {
          try(Socket sock=activeWifi==null?new Socket():activeWifi.getSocketFactory().createSocket()){
            sock.connect(new InetSocketAddress(ip,10000),350);
            found.add(ip);
          }catch(Exception ignored){}
        }));
      }
      pool.shutdown();
      try{pool.awaitTermination(12,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
      pool.shutdownNow();
      org.json.JSONArray hits=new org.json.JSONArray();for(String ip:found)hits.put(ip);
      org.json.JSONArray routes=new org.json.JSONArray();for(String ip:gateways)routes.put(ip);
      JSObject result=new JSObject();result.put("hosts",hits);result.put("gateways",routes);result.put("wifiDetected",activeWifi!=null);
      getActivity().runOnUiThread(() -> call.resolve(result));
    },"kait-lan-scan").start();
  }
  private boolean isWifi(String name){String n=name.toLowerCase();return n.startsWith("wlan")||n.startsWith("ap")||n.startsWith("swlan")||n.startsWith("softap")||n.startsWith("wifi")||n.startsWith("eth");}
  private org.json.JSONArray getIPs(){
    org.json.JSONArray ips=new org.json.JSONArray();
    try {Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();while(nets.hasMoreElements()){
      NetworkInterface net=nets.nextElement();if(!net.isUp()||net.isLoopback()||!isWifi(net.getName()))continue;
      Enumeration<InetAddress> adds=net.getInetAddresses();while(adds.hasMoreElements()){
        InetAddress a=adds.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())ips.put(a.getHostAddress());
      }
    }}catch(Exception ignored){} return ips;
  }
  private static class Room {
    String code,hostToken; WebSocket host; JSONObject state;
    Map<String,WebSocket> players=new HashMap<>();
    Map<String,String> tokens=new HashMap<>();
    Map<String,Integer> chips=new HashMap<>();
  }
  private static class LocalServer extends WebSocketServer {
    private final CountDownLatch started=new CountDownLatch(1);
    boolean waitForStart(){try{return started.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}
    private final Map<String,Room> rooms=new HashMap<>();
    private final Map<WebSocket,Room> belonging=new HashMap<>();
    private final Map<WebSocket,String> identities=new HashMap<>();
    LocalServer(int port){super(new InetSocketAddress("0.0.0.0",port));setReuseAddr(true);setConnectionLostTimeout(25);}
    private void send(WebSocket s, JSONObject msg){if(s!=null&&s.isOpen())try{s.send(msg.toString());}catch(Exception ignored){}}
    private JSONObject obj(String type){JSONObject j=new JSONObject();try{j.put("type",type);}catch(Exception ignored){}return j;}
    private void error(WebSocket s,String code,String message){JSONObject j=obj("error");try{j.put("code",code);j.put("message",message);}catch(Exception ignored){}send(s,j);}
    private void leave(WebSocket s){Room r=belonging.remove(s);String id=identities.remove(s);if(r==null)return;
      if(r.host==s){r.host=null;for(WebSocket c:r.players.values())error(c,"host-offline","Host disconnected. Waiting for reconnection…");}
      else if(id!=null&&r.players.get(id)==s){r.players.remove(id);JSONObject m=obj("offline");try{m.put("id",id);}catch(Exception ignored){}send(r.host,m);}
    }
    @Override public synchronized void onOpen(WebSocket c,ClientHandshake h){}
    @Override public synchronized void onMessage(WebSocket c,String raw){
      try{if(raw.length()>2097152)return;JSONObject m=new JSONObject(raw);String type=m.optString("type");
        if(type.equals("list")){
          JSONObject reply=obj("rooms");org.json.JSONArray found=new org.json.JSONArray();
          for(Room room:rooms.values())if(room.host!=null&&room.host.isOpen()){
            JSONObject item=new JSONObject();item.put("code",room.code);
            item.put("players",1+room.players.size());found.put(item);
          }
          reply.put("rooms",found);send(c,reply);return;
        }
        if(type.equals("create")){
          String code=m.optString("code").toUpperCase(),token=m.optString("token");
          if(!code.matches("[A-Z2-9]{4}")||token.length()<8){error(c,"invalid","Invalid room");return;}
          Room r=rooms.get(code);if(r!=null&&(r.host!=null||!token.equals(r.hostToken))){error(c,"code-taken","Room code used");return;}
          leave(c);if(r==null){r=new Room();r.code=code;r.hostToken=token;rooms.put(code,r);}r.host=c;belonging.put(c,r);
          JSONObject reply=obj("created");reply.put("code",code);send(c,reply);
          for(Map.Entry<String,WebSocket> e:r.players.entrySet()){JSONObject hello=obj("hello");String id=e.getKey();hello.put("id",id);hello.put("token",r.tokens.get(id));hello.put("chips",r.chips.get(id));send(c,hello);}return;
        }
        if(type.equals("join")){
          String code=m.optString("code").toUpperCase(),token=m.optString("token");Room r=rooms.get(code);
          if(r==null){error(c,"room-not-found","Room not found");return;}
          if(r.host==null){error(c,"room-busy","Host is reconnecting");return;}
          if(token.length()<1||token.length()>100){error(c,"invalid","Invalid player");return;}
          String id="p-"+token;Set<String> known=new HashSet<>(r.players.keySet());known.add("h");
          if(r.state!=null){org.json.JSONArray arr=r.state.optJSONArray("players");if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject p=arr.optJSONObject(i);if(p!=null)known.add(p.optString("id"));}}
          if(!known.contains(id)&&known.size()>=20){error(c,"room-full","This room is full (20/20)");return;}
          leave(c);WebSocket old=r.players.get(id);if(old!=null&&old!=c){belonging.remove(old);identities.remove(old);old.close(1000,"Rejoined");}
          r.players.put(id,c);r.tokens.put(id,token);r.chips.put(id,m.optInt("chips",1000));belonging.put(c,r);identities.put(c,id);
          JSONObject joined=obj("joined");joined.put("id",id);send(c,joined);
          JSONObject hello=obj("hello");hello.put("id",id);hello.put("token",token);hello.put("chips",r.chips.get(id));send(r.host,hello);
          if(r.state!=null){JSONObject state=obj("state");state.put("state",r.state);send(c,state);}return;
        }
        Room r=belonging.get(c);if(r==null)return;
        if(type.equals("state")&&r.host==c){JSONObject state=m.optJSONObject("state");if(state==null||!r.code.equals(state.optString("code")))return;r.state=state;JSONObject update=obj("state");update.put("state",state);for(WebSocket p:r.players.values())send(p,update);}
        else if(type.equals("action")&&r.host!=c){String id=identities.get(c);if(id!=null&&r.players.get(id)==c){JSONObject action=obj("action");action.put("id",id);action.put("action",m.optJSONObject("action"));send(r.host,action);}}
      }catch(Exception ignored){}
    }
    @Override public synchronized void onClose(WebSocket c,int code,String reason,boolean remote){leave(c);}
    @Override public void onError(WebSocket c,Exception ex){}
    @Override public void onStart(){started.countDown();}
  }
}
