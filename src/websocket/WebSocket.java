package websocket;
import java.util.*;
public abstract class WebSocket{
	private WebSocketThread wsThread=null;
	public String path;
	private String id;
	public HashMap<String,String>query;
	public final void init(String p,HashMap<String,String>q,WebSocketThread wst){
		path=p;query=q;
		id="";
		for(int i=0;i<8;i++)id+="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".charAt((int)(62*Math.random()));
		wsThread=wst;
		try{onopen();}catch(Exception e){e.printStackTrace();}
		synchronized(webSocketMap){webSocketMap.put(id,this);}
	}
	public abstract void onopen();
	public abstract void onclose();
	public abstract void onmessage(String msg);
	public void send(String msg){wsThread.pushQueue(msg);}
	public void close(){wsThread.notifyClosed();}
	public String getID(){return id;}
	void closed(){synchronized(webSocketMap){webSocketMap.remove(id);}}
	private static HashMap<String,WebSocket>webSocketMap=new HashMap<String,WebSocket>();
	public static void sendTo(String id,String msg){
		WebSocket ws=null;
		synchronized(webSocketMap){ws=webSocketMap.get(id);}
		if(ws!=null)ws.send(msg);
	}
};
