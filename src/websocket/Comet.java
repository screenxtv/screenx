package websocket;
import java.io.*;
import java.util.*;
import java.net.*;

class CometWSThread extends WebSocketThread{
	static class CometID{
		String id;
		InetAddress addr;
		CometID(String id,InetAddress addr){this.id=id;this.addr=addr;}
		public int hashCode(){return id.hashCode()+addr.hashCode();}
		public boolean equals(Object o){
			CometID c=(CometID)o;
			return id.equals(c.id)&&addr.equals(c.addr);
		}
	}
	static int serialNum=0;
	static final byte[]alphanumeric="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".getBytes();
	static String serialID(int a,int b){
		int snum;
		synchronized(alphanumeric){snum=serialNum++;}
		byte sid[]=new byte[a+b];
		for(int i=0;i<a;i++){sid[i]=alphanumeric[(int)(snum%alphanumeric.length)];snum/=alphanumeric.length;}
		for(int i=0;i<b;i++)sid[a+i]=alphanumeric[(int)(alphanumeric.length*Math.random())];
		return new String(sid);
	}
	static byte[]hex16="0123456789abcdef".getBytes();
	static String hexLength(int n){
		byte[]b=new byte[8];
		for(int i=0;i<8;i++)b[8-i-1]=hex16[(n>>(4*i))&0xf];
		return new String(b);
	}
	static HashMap<CometID,CometWSThread>cometMap=new HashMap<CometID,CometWSThread>();
	
	static CometWSThread createComet(WebSocket ws,String path,HashMap<String,String>query,InetAddress addr){
		CometWSThread wst=new CometWSThread(ws,path,null);
		String id=serialID(8,8);
		CometID cid=new CometID(id,addr);
		wst.setID(cid);
		synchronized(cometMap){
			cometMap.put(cid,wst);
		}
		return wst;
	}
	static CometWSThread getComet(CometID cid){
		synchronized(cometMap){return cometMap.get(cid);}
	}
	static final int TIMEOUT=15000,CLOSETIMEOUT=5000;

	CometID cid;
	boolean alive=true;
	void setID(CometID cid){this.cid=cid;}
	WebSocket ws;
	CometWSThread(WebSocket ws,String path,HashMap<String,String>query){
		websocket=ws;
		websocket.init(path,query,this);
		new Thread(this).start();
	}
	void handshake(String path,HashMap<String,String>header){}
	public void readLoop(){}
	public void writeLoop(){}
	int mode[]={0,0};
	void setTimeout(){notifyTimeout(CLOSETIMEOUT);}
	void clearTimeout(){notifyTimeout(0);}
	void exitTimeout(){notifyTimeout(-1);}
	void notifyTimeout(int t){synchronized(mode){mode[0]++;mode[1]=t;mode.notify();}}

	public void run(){
		setTimeout();
		try{
			while(true){
				synchronized(mode){
					int m=mode[0];
					if(mode[1]==-1)break;
					if(mode[1]==0)mode.wait();
					else mode.wait(mode[1]);
					if(m==mode[0])break;
				}
			}
		}catch(Exception e){}
		notifyClosed();
	}
	public void notifyClosed(){
		boolean rmv=false;
		synchronized(sendQueue){
			if(alive){
				rmv=true;
				alive=false;
				exitTimeout();
				pushQueue(null);
			}
		}
		if(rmv){
			synchronized(cometMap){
				cometMap.remove(cid);
				websocket.onclose();
			}
		}
	}
	String getQueue(){
		clearTimeout();
		StringBuffer buf=new StringBuffer();
		synchronized(sendQueue){
			if(sendQueue.size()==0&&alive)try{sendQueue.wait(TIMEOUT);}catch(Exception e){}
			for(String str:sendQueue){
				if(str==null||!alive){buf.append("ffffffff");break;}
				buf.append(hexLength(str.length()));
				buf.append(str);
			}
			sendQueue.clear();
		}
		setTimeout();
		return buf.toString();
	}
	
	public static String cometAction(WebSocketGenerator wsgen,String path,boolean secure,String post,InetAddress addr){
		char type=post.charAt(0);
		if(type=='+'){
			CometWSThread comet=createComet(wsgen.create(path,secure,false),path,new HashMap<String,String>(),addr);
			System.out.println("NEW COMET"+comet.cid.id+" "+addr+" "+comet.cid.hashCode());
			System.out.println(new CometID(comet.cid.id,addr).equals(new CometID(comet.cid.id+"",addr))+"EQTEST");
			return comet.cid.id;
		}
		String id=post.substring(1,1+16);
		CometWSThread comet=getComet(new CometID(id,addr));
		if(comet==null){System.out.println("NOCOMET"+id+" "+addr+" "+new CometID(id,addr).hashCode());}
		if(comet==null)return "ffffffff";
		switch(type){
		case '>':{
			int index=1+16,length=post.length();
			while(index<length){
				int len=Integer.parseInt(post.substring(index,index+8),16);
				comet.websocket.onmessage(post.substring(index+8,index+8+len));
				index+=8+len;
			}
			return "";}
		case '<':
			return comet.getQueue();
		case '-':
			comet.notifyClosed();
			return "";
		}
		System.out.println("ERR("+id+")"+type);
		return "ffffffff";
	}
}
