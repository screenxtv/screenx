import websocket.*;
import vt100.*;
import java.io.*;
import java.util.*;

public class ScreenX implements WebSocketGenerator{
	static int DEFAULT_W,DEFAULT_H;
	static Chat chat=new Chat();
	static boolean loginEnabled=false;
	static boolean httpLoginEnabled=false;
	public static void main(String args[])throws Exception{
		Config config=new Config(new File("screenx.conf"));
		int port=config.getInteger("HttpPort",-1);
		int sport=config.getInteger("HttpsPort",-1);
		DEFAULT_W=TermConnector.W=config.getInteger("Width",100);
		DEFAULT_H=TermConnector.H=config.getInteger("Height",30);
		loginEnabled=config.getBoolean("EnableLogin");
		if(loginEnabled&&config.getBoolean("EnableHttpLogin"))httpLoginEnabled=true;

		String ksfile=config.getString("KeyStoreFile");
		String kspswd=config.getString("KeyStorePassword");
		String loginpswd=config.getString("LoginPassword");
		String docRootPath=config.getString("DocumentRoot");
		File documentRoot=docRootPath==null?null:new File(docRootPath);
		if(!documentRoot.isDirectory())documentRoot=null;
		SXLogin.setPassword(loginpswd);
		WebSocketGenerator wsgen=new ScreenX();
		if(port>0)new WebSocketServer(documentRoot,port,wsgen).start();
		if(sport>0)new WebSocketServer(documentRoot,sport,wsgen,new FileInputStream(new File(ksfile)),kspswd).start();
	}
	public WebSocket create(String path,boolean secure){
		if(path.indexOf("sxlogin")>=0){
			if(loginEnabled&&(httpLoginEnabled||secure))return new ScreenXSessionLogin();
			return null;
		}
		if(path.indexOf("login")>=0){
			if(loginEnabled&&(httpLoginEnabled||secure))return new SXLogin();
			return null;
		}
		if(path.indexOf("screenx")>=0){
			return new ScreenXSession();
		}
		return null;
	}
}



class TermConnector implements Runnable{
	static int W,H;
	public Terminal terminal;
	Thread thread=null;
	public TermConnector(){
		thread=new Thread(this);
		thread.start();
	}
	Timer timer=new Timer();
	Task task=null;
	class Task extends TimerTask{
		TermConnector self;
		Task(TermConnector t){self=t;}
		public void run(){
			synchronized(self){
				if(task==this){try{in.close();in=null;task=null;}catch(Exception e){}}
			}
		}
	}
	public synchronized Terminal tryConnect(){
		if(task!=null){task.cancel();task=null;}
		count++;
		System.out.println(count);
		System.out.println("a");
		if(terminal==null){notify();try{wait();}catch(Exception e){}}
		System.out.println("b"+terminal);
		return terminal;
	}
	int count=0;
	public synchronized void tryClose(){
		count--;
		System.out.println(count);
		if(count==0){
			timer.schedule(task=new Task(this),5000);
		}
	}
	InputStream in=null;
	public void run(){
		try{
		while(true){
			synchronized(this){
				System.out.println("waiting");
				if(count==0)try{wait();}catch(Exception e){}
				System.out.println("start");
			}
			Process proc=null;
			InputStream in=null;
			OutputStream out=null;
			Terminal terminal=null;
			try{
				String env[]={"TERM=vt100","LANG=ja_JP.UTF-8"};
				proc=Runtime.getRuntime().exec("./screenxfork "+W+" "+H,env);
				in=proc.getInputStream();
				out=proc.getOutputStream();
				terminal=new Terminal(W,H,in=proc.getInputStream());
			}catch(Exception e){System.out.println(e.toString());e.printStackTrace();}
			synchronized(this){
				this.in=in;
				this.terminal=terminal;
				notifyAll();
			}
			try{
				System.out.println("main");
				terminal.main();
				System.out.println("end");
			}catch(Exception e){System.out.println(e.toString());e.printStackTrace();}
			synchronized(this){
				this.terminal=null;
				try{in.close();}catch(Exception e){}
				try{out.close();}catch(Exception e){}
				System.out.println("destroying");
				try{proc.waitFor();}catch(Exception e){}
				System.out.println("destroy");
			}
		}
		}catch(Exception e){e.printStackTrace();}
	}
}

interface ChatListener{public void onChatMessage(String data);}
class Chat{
	HashSet<ChatListener>listeners=new HashSet<ChatListener>();
	LinkedList<String>messages=new LinkedList<String>();
	int MAX_MESSAGES=40;
	public synchronized void addListener(ChatListener l){
		listeners.add(l);
		for(String msg:messages)l.onChatMessage("M"+msg);
		broadcast("N"+listeners.size());
	}
	public synchronized void removeListener(ChatListener l){
		listeners.remove(l);
		broadcast("N"+listeners.size());
	}
	private synchronized void broadcast(String msg){
		for(ChatListener l:listeners)l.onChatMessage(msg);
	}
	public synchronized void addMessage(String msg){
		messages.addLast(msg);
		if(messages.size()>MAX_MESSAGES)messages.removeFirst();
		broadcast("M"+msg);
	}
}

class ScreenXSession extends WebSocket implements Runnable,ChatListener{
	static TermConnector tc=new TermConnector();
	Terminal terminal;
	Thread thread;
	public void onopen(){
		terminal=tc.tryConnect();
		(thread=new Thread(this)).start();
	}
	boolean added=false;
	public void run(){
		try{
			System.out.println("sendsize");
			send(terminal.getWidth()+"x"+terminal.getHeight());
		}catch(Exception e){}
		ScreenX.chat.addListener(this);
		try{
			VTBuf vtbuf=new VTBuf(1,64*1024);
			while(loop){
				terminal.read(vtbuf);
				if(vtbuf.read<0)break;
				if(vtbuf.read>0)send(new String(vtbuf.buf,0,vtbuf.read+vtbuf.offset,"UTF-8"));
			}
		}catch(Exception e){}
		ScreenX.chat.removeListener(this);
		close();
	}
	public void onChatMessage(String msg){
		try{send(msg);}catch(Exception e){}
	}
	boolean loop=true;
	public void onmessage(String msg){
		ScreenX.chat.addMessage(msg);
	}
	public void onclose(){
		thread.interrupt();
		System.out.println("onclose");loop=false;tc.tryClose();
	}
}



class SXLogin extends WebSocket implements Runnable{
	Thread mainthread=null;
	
	private static String pswd;
	public static void setPassword(String p){pswd=p;}
	static String random16(){
		String s="";
		for(int i=0;i<16;i++)s+=inttoc16((int)(16*Math.random()));
		return s;
	}
	static char inttoc16(int i){return "0123456789ABCDEF".charAt(i);}
	static int c16toint(char c){return c<='9'?c-'0':10+c-'A';}
	static String hash(String rnd,String pss){
		int arr[]=new int[16];
		for(int i=0;i<16;i++)arr[i]=i+c16toint(rnd.charAt(i));
		for(int i=0;i<10;i++){
			for(int j=0;j<pss.length();j++){
				int c=pss.charAt(j);
				for(int k=0;k<16;k++){
					arr[(i*j+c+3*k)%16]+=(arr[(c*j+7*c+5*k)%16]*arr[(c*i+5*c+7*k)%16]+c+k)%16;
				}
			}
		}
		String s="";
		for(int i=0;i<16;i++)s+=inttoc16(arr[i]%16);
		return s;
	}
	static boolean checkPassword(String str,String rnd){
		return str.equals(hash(rnd,pswd));
	}
	
	
	OutputStream pout=null;
	public void onopen(){
		rnd=random16();
		send("OK"+rnd);
	}
	String rnd;
	String initstr;
	Process proc=null;
	public void run(){
		try{
			String param[]=initstr.split(":");
			System.out.println(initstr);
			String name=param[0];
			for(int i=0;i<name.length();i++){char c=name.charAt(i);if(c!='_'&&!('a'<=c&&c<='z')&&!('A'<=c&&c<='Z')&&!('0'<=c&&c<='9')){send("NGscreenname error");return;}}
			String size[]=param[1].split("x");
			int w=Integer.parseInt(size[0]),h=Integer.parseInt(size[1]);
			String ps=param[2];
			if(!checkPassword(ps,rnd)){
				send("NGwrong password");System.out.println("wrong");Thread.sleep(1000);close();return;
			}
			System.out.println("right");
			send("OK");
			String env[]={"TERM=vt100","LANG=ja_JP.UTF-8"};
			proc=Runtime.getRuntime().exec("./screenxfork "+w+" "+h+" "+name,env);
			pout=proc.getOutputStream();
			InputStream pin=proc.getInputStream();
			int rlen=0;byte buf[]=new byte[65536];int index=0;
			while((rlen=pin.read(buf,index,buf.length-index))>0){
				int len=rlen+index;int wlen=0;
				for(wlen=0;wlen<len;){
					byte c=buf[wlen];
					if((buf[wlen]&0x80)==0)wlen++;
					else{
						int ulen;
						if((c&0x20)==0){if(wlen+1<len)ulen=2;else break;}
						else if((c&0x10)==0){if(wlen+2<len)ulen=3;else break;}
						else if((c&0x8)==0){if(wlen+3<len)ulen=4;else break;}
						else if((c&0x4)==0){if(wlen+4<len)ulen=5;else break;}
						else if((c&0x2)==0){if(wlen+5<len)ulen=6;else break;}
						else{buf[wlen]&=0x7f;wlen++;continue;}
						for(int i=1;i<ulen;i++)buf[wlen+i]=(byte)((buf[wlen+i]&0x3F)|0x80);
						wlen+=ulen;
					}
				}
				send(new String(buf,0,wlen,"UTF-8"));
				for(int i=wlen;i<len;i++)buf[i-wlen]=buf[i];
				index=len-wlen;
			}
		}catch(Exception e){e.printStackTrace();}
		close();
	}
	public void onmessage(String msg){
		if(mainthread==null){
			initstr=msg;
			mainthread=new Thread(this);
			mainthread.start();
		}
		try{
			pout.write(msg.getBytes("UTF-8"));
			pout.flush();
		}catch(Exception e){mainthread.interrupt();}
	}
	public void onclose(){
		System.out.println("closed");
		mainthread.interrupt();
		try{proc.destroy();}catch(Exception e){}
	}
}





class ScreenXSessionLogin extends WebSocket implements Runnable,ChatListener{
	Thread mainthread=null;
	OutputStream pout=null;
	public void onopen(){
		rnd=SXLogin.random16();
		send("OK"+rnd);
	}
	String rnd;
	Process proc=null;
	public void run(){
		InputStream pin;
		try{
			send("OK"+ScreenX.DEFAULT_W+"x"+ScreenX.DEFAULT_H);
			String env[]={"TERM=vt100","LANG=ja_JP.UTF-8"};
			proc=Runtime.getRuntime().exec("./screenxfork "+ScreenX.DEFAULT_W+" "+ScreenX.DEFAULT_H,env);
			pout=proc.getOutputStream();
			pin=proc.getInputStream();
		}catch(Exception e){e.printStackTrace();close();return;}
		ScreenX.chat.addListener(this);
		try{
			int rlen=0;byte buf[]=new byte[65536];int index=1;
			while((rlen=pin.read(buf,index,buf.length-index))>0){
				int len=rlen+index;int wlen=0;
				for(wlen=0;wlen<len;){
					byte c=buf[wlen];
					if((buf[wlen]&0x80)==0)wlen++;
					else{
						int ulen;
						if((c&0x20)==0){if(wlen+1<len)ulen=2;else break;}
						else if((c&0x10)==0){if(wlen+2<len)ulen=3;else break;}
						else if((c&0x8)==0){if(wlen+3<len)ulen=4;else break;}
						else if((c&0x4)==0){if(wlen+4<len)ulen=5;else break;}
						else if((c&0x2)==0){if(wlen+5<len)ulen=6;else break;}
						else{buf[wlen]&=0x7f;wlen++;continue;}
						for(int i=1;i<ulen;i++)buf[wlen+i]=(byte)((buf[wlen+i]&0x3F)|0x80);
						wlen+=ulen;
					}
				}
				send(new String(buf,0,wlen,"UTF-8"));
				for(int i=wlen;i<len;i++)buf[1+i-wlen]=buf[i];
				index=len-wlen+1;
			}
		}catch(Exception e){e.printStackTrace();}
		ScreenX.chat.removeListener(this);
		close();
	}
	public void onChatMessage(String msg){
		send(msg);
	}
	public void onmessage(String msg){
		if(mainthread==null){
			if(!SXLogin.checkPassword(msg,rnd)){send("NGwrong password");System.out.println("wrong");close();return;}
			mainthread=new Thread(this);
			mainthread.start();
			return;
		}
		try{
			pout.write(msg.getBytes("UTF-8"));
			pout.flush();
		}catch(Exception e){mainthread.interrupt();}
	}
	public void onclose(){
		System.out.println("closed");
		mainthread.interrupt();
		try{proc.destroy();}catch(Exception e){}
	}
}


class Config{
	HashMap<String,String>map=new HashMap<String,String>();
	public Config(File file)throws Exception{
		BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(file)));
		String line;
		while((line=br.readLine())!=null){
			int index=line.indexOf(":");
			if(index<0)continue;
			String key=line.substring(0,index).trim().toLowerCase();
			String value=line.substring(index+1).trim();
			map.put(key,value);
		}
	}
	String getString(String key){String val=map.get(key.toLowerCase());return val!=null?val:"";}
	int getInteger(String key,int defaultVal){try{return Integer.parseInt(map.get(key.toLowerCase()));}catch(Exception e){return defaultVal;}}
	boolean getBoolean(String key){
		String val=map.get(key.toLowerCase());
		if(val==null)return false;
		val=val.toLowerCase();
		return val.equals("true")||val.equals("yes")||val.equals("1");
	}
}
