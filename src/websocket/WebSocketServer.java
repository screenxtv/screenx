package websocket;
import java.security.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class WebSocketServer extends Thread{
	ServerSocket server;
	WebSocketGenerator webSocketGenerator;
	File documentRoot;
	boolean secure=false;
	public WebSocketServer(File root,int port,WebSocketGenerator wsgen)throws Exception{
		this.documentRoot=root;
		server=new ServerSocket(port);this.webSocketGenerator=wsgen;
	}
	public WebSocketServer(File root,int port,Class<? extends WebSocket>wsClass)throws Exception{
		this.documentRoot=root;
		server=new ServerSocket(port);
		final Class<? extends WebSocket> wsc=wsClass;
		this.webSocketGenerator=new WebSocketGenerator(){public WebSocket create(String path,boolean secure){try{return wsc.newInstance();}catch(Exception e){return null;}}};
	}
	public WebSocketServer(File root,int port,WebSocketGenerator wsgen,InputStream ksin,String kspass)throws Exception{
		this.documentRoot=root;
		this.webSocketGenerator=wsgen;
		setServerSocket(ksin,kspass,port);
	}
	public WebSocketServer(File root,int port,Class<? extends WebSocket>wsClass,InputStream ksin,String kspass)throws Exception{
		this.documentRoot=root;
		final Class<? extends WebSocket> wsc=wsClass;
		this.webSocketGenerator=new WebSocketGenerator(){public WebSocket create(String path,boolean secure){try{return wsc.newInstance();}catch(Exception e){return null;}}};
		setServerSocket(ksin,kspass,port);
	}
	void setServerSocket(InputStream ksin,String kspass,int port)throws Exception{
		char[]ps=kspass.toCharArray();
		KeyStore ks=KeyStore.getInstance("JKS");
		ks.load(ksin,ps);
		KeyManagerFactory kmf=KeyManagerFactory.getInstance("SunX509");
		kmf.init(ks,ps);
		SSLContext sslContext=SSLContext.getInstance("TLS");
		sslContext.init(kmf.getKeyManagers(),null,null);
		server=sslContext.getServerSocketFactory().createServerSocket(port);
		secure=true;
	}
	public void run(){
		try{
			while(true){
				new HTTPThread(documentRoot,webSocketGenerator,server.accept(),secure).start();
			}
		}catch(Exception e){e.printStackTrace();}
	}
}



class HTTPThread extends Thread{
	Socket socket;
	int MAX_POST_LENGTH=1000000;
	WebSocketGenerator webSocketGenerator;
	boolean secure;
	File documentRoot;
	HTTPThread(File root,WebSocketGenerator wsgen,Socket socket,boolean sec){documentRoot=root;webSocketGenerator=wsgen;this.socket=socket;secure=sec;}
	static String readLine(InputStream in)throws Exception{
		StringBuffer buf=new StringBuffer();
		while(true){
			int c=in.read();
			if(c==-1||c=='\n')break;
			if(c!='\r')buf.append((char)c);
		}
		return buf.toString();
	}
	public void run(){
		try{
			InputStream in=new BufferedInputStream(socket.getInputStream());
			OutputStream out=new BufferedOutputStream(socket.getOutputStream());
			while(true){
				String request[]=readLine(in).split(" ");
				if(request.length!=3)break;
				String method=request[0],path=request[1],httpVer=request[2];
				HashMap<String,String>query=new HashMap<String,String>();
				int queryIndex=path.indexOf("?");
				if(queryIndex!=-1){
					for(String kv:path.substring(queryIndex+1).split("&")){
						int kvIndex=kv.indexOf("=");
						if(kvIndex==-1)query.put(kv,"");
						else query.put(kv.substring(0,kvIndex),kv.substring(kvIndex+1));
					}
					path=path.substring(0,queryIndex);
				}
				HashMap<String,String>header=new HashMap<String,String>();
				while(true){
					String kv=readLine(in);
					if(kv.length()==0)break;
					int colon=kv.indexOf(":");
					String key=kv.substring(0,colon);
					String value=kv.substring(colon+(colon+1<kv.length()&&kv.charAt(colon+1)==' '?2:1));
					header.put(key.toLowerCase(),value);
				}
				String upgrade=header.get("upgrade");
				if(upgrade==null){
					if(method.equals("GET")){
						if(httpReply(method,path,query,header,in,out))continue;
					}else if(method.equals("POST")){
						try{
							String contentLength=header.get("content-length");
							int len=0;
							try{len=Integer.parseInt(contentLength);}catch(Exception e){}
							if(len==0||len>MAX_POST_LENGTH)break;
							byte[]data=new byte[Integer.parseInt(contentLength)];
							in.read(data,0,len);
							String post=new String(data);
							System.out.println(path+"#"+post);
							byte odata[]=CometWSThread.cometAction(webSocketGenerator,path,secure,post).getBytes("UTF-8");
							String oheader="HTTP/1.0 200 OK\r\n";
							oheader+="Content-Length: "+odata.length+"\r\n";
							out.write((oheader+"\r\n").getBytes());
							out.write(odata);
							out.flush();
						}catch(Exception e){e.printStackTrace();}
						continue;
					}
					else System.out.println(method);
				}else if(upgrade.toLowerCase().equals("websocket")){
					WebSocket ws=webSocketGenerator.create(path,secure);
					if(ws==null){out.write("HTTP/1.0 404 NotFound\r\n\r\n".getBytes());out.flush();break;}
					websocketVersionSwitch(ws,path,query,header,in,out).start();
				}
				break;
			}
		}catch(Exception e){e.printStackTrace();}
		try{socket.close();}catch(Exception e){}
	}
	boolean httpReply(String method,String path,HashMap<String,String>query,HashMap<String,String>header,InputStream in,OutputStream out)throws Exception{
		if(documentRoot!=null)HTTPFileManager.respond(documentRoot,path,out);
		else{
			String msg="No DocumentRoot on this server.";
			out.write(("HTTP/1.0 503 OK\r\nContent-Length: "+msg.length()+"\r\n\r\n"+msg).getBytes());
			out.close();
			return false;
		}
		return true;
	}
/*	boolean cometReply(String post,String path,HashMap<String,String>query,HashMap<String,String>header,InputStream in,OutputStream out)throws Exception{
		out.write(("HTTP/1.0 404 NotFound\r\nContent-Length:"+post.length()+"\r\n\r\n"+post.toUpperCase()).getBytes());
//		"Keep-Alive: timeout=15,max=100\r\n";
//		"Connection: Keep-Alive\r\n";
		out.flush();
		return false;
		}*/
	WebSocketThread websocketVersionSwitch(WebSocket ws,String path,HashMap<String,String>query,HashMap<String,String>header,InputStream in,OutputStream out)throws Exception{
		String ver=header.get("sec-websocket-version");
		if("13".equals(ver))
			return new WebSocketThread13(ws,path,query,header,in,out);
		if(header.get("sec-websocket-key1")!=null&&header.get("sec-websocket-key2")!=null)
			return new WebSocketThreadKV12(ws,path,query,header,in,out);
		throw new Exception("unknown websocket version: "+ver+" "+header.get("sec-websocket-key"));
	}
}

abstract class WebSocketThread implements Runnable{
	InputStream in;OutputStream out;
	WebSocket websocket;
	boolean isAlive=true;
	LinkedList<String>sendQueue=new LinkedList<String>();
	void pushQueue(String msg){synchronized(sendQueue){sendQueue.add(msg);sendQueue.notify();}}
	String getQueue(){
		synchronized(sendQueue){
			if(writeThread.isInterrupted())return null;
			if(sendQueue.size()==0)try{sendQueue.wait();}catch(Exception e){}
			return sendQueue.remove();
		}
	}
	synchronized void notifyClosed(){
		if(isAlive)try{websocket.onclose();websocket.closed();}catch(Exception e){e.printStackTrace();}
		isAlive=false;
		try{in.close();}catch(Exception e){}
		try{out.close();}catch(Exception e){}
		synchronized(sendQueue){
			readThread.interrupt();
			writeThread.interrupt();
		}
	}
	abstract void handshake(String path,HashMap<String,String>header)throws Exception;
	WebSocketThread(){}
	WebSocketThread(WebSocket ws,String path,HashMap<String,String>query,HashMap<String,String>header,InputStream in,OutputStream out)throws Exception{
		this.in=in;this.out=out;
		handshake(path,header);
		websocket=ws;
		websocket.init(path,query,this);
	}
	Thread readThread,writeThread;
	void start(){
		readThread=Thread.currentThread();
		writeThread=new Thread(this);
		writeThread.start();
		readLoop();
	}
	public void run(){writeLoop();}
	abstract void writeLoop();
	abstract void readLoop();
}

class Digest{
	static MessageDigest digest_md5,digest_sha1;
	static{
		try{digest_md5=MessageDigest.getInstance("MD5");}catch(Exception e){}
		try{digest_sha1=MessageDigest.getInstance("SHA1");}catch(Exception e){}
	}
	static byte[]md5(byte[]b){synchronized(digest_md5){return digest_md5.digest(b);}}
	static byte[]sha1(byte[]b){synchronized(digest_sha1){return digest_sha1.digest(b);}}
}

class WebSocketThreadKV12 extends WebSocketThread{
	void handshake_key(String key,byte[]b,int index){
		long num=0,cnt=0;
		for(int i=0;i<key.length();i++){
			int c=key.charAt(i);
			if('0'<=c&&c<='9')num=10*num+c-'0';
			if(c==' ')cnt++;
		}
		num/=cnt;
		for(int i=0;i<4;i++)b[index+i]=(byte)(num>>(8*(3-i)));
	}
	void handshake(String path,HashMap<String,String>header)throws Exception{
		StringBuffer buf=new StringBuffer();
		buf.append("HTTP/1.1 101 WebSocket\r\n");
		buf.append("Upgrade: websocket\r\n");
		buf.append("Connection: Upgrade\r\n");
		String origin=header.get("origin");
		if(origin!=null)buf.append("Sec-WebSocket-Origin: "+origin+"\r\n");
		String host=header.get("host");
		if(host!=null)buf.append("Sec-WebSocket-Location: ws://"+host+path+"\r\n");
		buf.append("\r\n");
		byte[]md5data=new byte[16];
		handshake_key(header.get("sec-websocket-key1"),md5data,0);
		handshake_key(header.get("sec-websocket-key2"),md5data,4);
		in.read(md5data,8,8);
		out.write(buf.toString().getBytes());
		out.write(Digest.md5(md5data));
		out.flush();
	}
	WebSocketThreadKV12(WebSocket ws,String path,HashMap<String,String>query,HashMap<String,String>header,InputStream in,OutputStream out)throws Exception{
		super(ws,path,query,header,in,out);
	}
	void readLoop(){
		try{
			while(true){
				Vector<byte[]>buf=new Vector<byte[]>();
				if(in.read()!=0)throw new Exception("err");
				int ARRSIZE=1024;
				byte[]arr=new byte[ARRSIZE];
				int arrIndex=0;
				while(true){
					int c=in.read();
					if(c==-1)throw new Exception("closed");
					if(c==0xff)break;
					arr[arrIndex++]=(byte)c;
					if(arrIndex==arr.length){
						arrIndex=0;buf.add(arr);
						arr=new byte[ARRSIZE];
					}
				}
				byte data[]=new byte[ARRSIZE*buf.size()+arrIndex];
				for(int i=0;i<buf.size();i++){
					byte[]b=buf.get(i);
					for(int j=0;j<ARRSIZE;j++)data[ARRSIZE*i+j]=b[j];
				}
				for(int i=0;i<arrIndex;i++)data[ARRSIZE*buf.size()+i]=arr[i];
				try{websocket.onmessage(new String(data,"UTF-8"));}catch(Exception e){e.printStackTrace();}
			}
		}catch(Exception e){}
		notifyClosed();
	}
	
	void writeLoop(){
		try{
			while(true){
				byte[]data=getQueue().getBytes("UTF-8");
				out.write((byte)0x00);
				out.write(data);
				out.write((byte)0xff);
				out.flush();
			}
		}catch(Exception e){}
		notifyClosed();
	}
	
}

class WebSocketThread13 extends WebSocketThread{
	String convsha64(String str){
		byte[]b=Digest.sha1(str.getBytes());
		byte[]out=new byte[(b.length+2)/3*4];
		String b64="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
		for(int i=0,j=0;i<b.length;i+=3,j+=4){
			int a1=b[i]&0xff;
			int a2=i+1<b.length?b[i+1]&0xff:0;
			int a3=i+2<b.length?b[i+2]&0xff:0;
			out[j]=(byte)(b64.charAt(a1>>2));
			out[j+1]=(byte)(b64.charAt(((a1&0x3)<<4)|(a2>>4)));
			out[j+2]=(byte)(i+1<b.length?b64.charAt(((a2&0xf)<<2)|(a3>>6)):'=');
			out[j+3]=(byte)(i+2<b.length?b64.charAt(a3&0x3f):'=');
		}
		return new String(out);
	}
	void handshake(String path,HashMap<String,String>header)throws Exception{
		StringBuffer buf=new StringBuffer();
		buf.append("HTTP/1.1 101 WebSocket\r\n");
		buf.append("Upgrade: websocket\r\n");
		buf.append("Connection: Upgrade\r\n");
		buf.append("Sec-WebSocket-Accept: ");
		buf.append(convsha64(header.get("sec-websocket-key")+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"));
		buf.append("\r\n\r\n");
		out.write(buf.toString().getBytes());
		out.flush();
	}
	WebSocketThread13(WebSocket ws,String path,HashMap<String,String>query,HashMap<String,String>header,InputStream in,OutputStream out)throws Exception{
		super(ws,path,query,header,in,out);
	}
	static final int MAXLENGTH=1000000;
	void readLoop(){
		try{
			while(true){
				int framestart=in.read();
				if(framestart!=129)throw new Exception("unknown framestart");
				int lentype=in.read();
				if(lentype==-1)break;
				int length=0;
				if((lentype&0x7f)==0x7e){
					for(int i=0;i<2;i++){
						int c=in.read();if(c==-1)break;
						length=(length<<8)|c;
					}
				}else if((lentype&0x7f)==0x7f){
					for(int i=0;i<8;i++){
						int c=in.read();if(c==-1)break;
						length=(length<<8)|c;
					}
				}else length=lentype&0x7f;
				if(length>MAXLENGTH)throw new Exception("maxlength exceeded: "+MAXLENGTH);
				byte data[]=new byte[length];
				byte mask[]=new byte[4];
				in.read(mask,0,4);
				in.read(data,0,length);
				for(int i=0;i<length;i++)data[i]^=mask[i&0x3];
				try{websocket.onmessage(new String(data,"UTF-8"));}catch(Exception e){e.printStackTrace();}
			}
		}catch(Exception e){}
		notifyClosed();
	}
	void writeLoop(){
		try{
			while(true){
				byte[]data=getQueue().getBytes("UTF-8");
				out.write((byte)0x81);
				if(data.length<0x7e)out.write((byte)data.length);
				else if(data.length<0x10000){out.write((byte)0x7e);out.write((byte)(data.length>>8));out.write((byte)data.length);}
				out.write(data);
				out.flush();
			}
		}catch(Exception e){}
		notifyClosed();
	}
}


class HTTPFileManager{
	static String group="a-zA-Z0-9_\\-";
	static String pattern="^(/["+group+"]+["+group+"\\.]*)+$";
	static void respond(File documentRoot,String path,OutputStream out)throws Exception{
		if(path.equals("/"))path="/index.html";
		if(!path.matches(pattern)){
			out.write("HTTP/1.0 400 Bad Request\r\nContent-Length: 11\r\n\r\nBad Request".getBytes());
			out.flush();
			return;
		}
		File file=new File(documentRoot,path.substring(1));
		if(!file.exists()){
			out.write("HTTP/1.0 404 Not Found\r\nContent-Length: 13\r\n\r\n404 Not Found".getBytes());
			out.flush();
			return;
		}
		int length=(int)file.length();
		String header="HTTP/1.0 200 OK\r\n";
		header+="Content-Type: "+getContentType(path)+"\r\n";
		header+="Content-Length: "+length+"\r\n";
		header+="Keep-Alive: timeout=1,max=100\r\n";
		header+="Connection: Keep-Alive\r\n";
		out.write((header+"\r\n").getBytes());
		byte[]buf=new byte[8*1024];
		FileInputStream fin=new FileInputStream(file);
		while(length>0){
			int rb=fin.read(buf,0,buf.length<length?buf.length:length);
			if(rb<0)throw new IOException("fileread err");
			length-=rb;
			out.write(buf,0,rb);
		}
		out.flush();
	}
	static HashMap<String,String>exMap=new HashMap<String,String>();
	static{
		String ex[][]={
			{"txt","text/plain"},
			{"html","text/html"},
			{"css","text/css"},
			{"js","text/javascript"},
			{"jpg","image/jpeg"},
			{"png","image/png"}
		};
		for(String[]kv:ex)exMap.put(kv[0],kv[1]);
	}
	static String getContentType(String path){
		int dot=path.lastIndexOf(".");
		String type=dot<0?null:exMap.get(path.substring(dot+1).toLowerCase());
		return type!=null?type:"application/octet-stream";
			
	}
}