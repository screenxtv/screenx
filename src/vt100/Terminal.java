package vt100;
import java.io.*;

public class Terminal{
	class Line{
		int length,chars[],fonts[];
		Line(int size){chars=new int[size];fonts=new int[size];length=0;}
	}
	int W,H;
	Line line[],linetmp[];
	InputStream input;
	public Terminal(int w,int h,InputStream in){
		W=w;H=h;
		line=new Line[H];
		linetmp=new Line[H];
		input=in;for(int i=0;i<H;i++)line[i]=new Line(W);
	}
	public void main(){
		byte buf[]=new byte[65536];
		int rd;
		try{
			while((rd=input.read(buf))>=0){
				for(int i=0;i<rd;i++)write(buf[i]);
				synchronized(this){notifyAll();}
			}
		}catch(Exception e){e.printStackTrace();}
		synchronized(this){
			isTerminated=true;
			notifyAll();
		}
	}
	public int getWidth(){return W;}
	public int getHeight(){return H;}
	final int fontDefault=0x00088;
	int font=fontDefault;
	int scrollStart,scrollEnd;
	int cursorX,cursorY;
	boolean insertMode=false;
	boolean isTerminated=false;
	
	byte buffer[]=new byte[1024*1024];
	long bufferindex=0;
	byte bufferpushbuf[]=new byte[16];
	synchronized void bufferPush(int c){
		int len=encodeUTF8(c,bufferpushbuf,0);
		for(int i=0;i<len;i++)buffer[(int)(bufferindex++%buffer.length)]=bufferpushbuf[i];
	}
	
	static int encodeUTF8(int c,byte buf[],int index){
		if(c<1<<7)buf[index++]=(byte)c;
		else if(c<1<<11){
			buf[index++]=(byte)(0xc0|((c>>6)&0x1f));
			buf[index++]=(byte)(0x80|(c&0x3f));
		}else if(c<1<<16){
			buf[index++]=(byte)(0xe0|((c>>12)&0xf));
			buf[index++]=(byte)(0x80|((c>>6)&0x3f));
			buf[index++]=(byte)(0x80|(c&0x3f));
		}else if(c<1<<21){
			buf[index++]=(byte)(0xf0|((c>>18)&0x7));
			buf[index++]=(byte)(0x80|((c>>12)&0xf));
			buf[index++]=(byte)(0x80|((c>>6)&0x3f));
			buf[index++]=(byte)(0x80|(c&0x3f));
		}
		else if(c<1<<26){
			buf[index++]=(byte)(0xf8|((c>>24)&0x3));
			buf[index++]=(byte)(0x80|((c>>18)&0x7));
			buf[index++]=(byte)(0x80|((c>>12)&0xf));
			buf[index++]=(byte)(0x80|((c>>6)&0x3f));
			buf[index++]=(byte)(0x80|(c&0x3f));
		}else{
			buf[index++]=(byte)(0xf4|((c>>30)&0x1));
			buf[index++]=(byte)(0x80|((c>>24)&0x3));
			buf[index++]=(byte)(0x80|((c>>18)&0x7));
			buf[index++]=(byte)(0x80|((c>>12)&0xf));
			buf[index++]=(byte)(0x80|((c>>6)&0x3f));
			buf[index++]=(byte)(0x80|(c&0x3f));
		}
		return index;
	}

	static int bytePushString(String s,byte buf[],int index){byte b[]=s.getBytes();for(int i=0;i<b.length;i++)buf[index+i]=b[i];return index+b.length;}
	public void read(VTBuf vtbuf)throws Exception{read(vtbuf,60*1000);}
	public synchronized void read(VTBuf vtbuf,int msec)throws Exception{
		if(isTerminated){vtbuf.version=vtbuf.read=-1;return;}
		if(vtbuf.terminal==this){
			if(vtbuf.version==bufferindex)wait(msec);
			if(isTerminated){vtbuf.version=vtbuf.read=-1;return;}
			if(vtbuf.version==bufferindex){vtbuf.read=0;return;}
			if(bufferindex-vtbuf.version<=vtbuf.buf.length){
				for(int i=0;i<bufferindex-vtbuf.version;i++)vtbuf.buf[vtbuf.offset+i]=buffer[(int)((vtbuf.version+i)%buffer.length)];
				vtbuf.read=(int)(bufferindex-vtbuf.version);
				vtbuf.version=bufferindex;
				return;
			}
		}
		vtbuf.terminal=this;
		int index=vtbuf.offset;
		index=bytePushString((char)0x1B+"[2J"+(char)0x1B+"[m"+(char)0x1B+"[1;"+H+"r",vtbuf.buf,index);
		for(int fontprev=fontDefault,y=0;y<H;y++){
			Line ln=line[y];
			for(int x=0;x<ln.length;x++){
				int font=ln.fonts[x],c=ln.chars[x];
				if(fontprev!=font){
					String param="";
					if(font!=fontDefault){
						if((font&0x11188)!=(fontprev&0x11188)){param="0";fontprev=fontDefault;}
						if((font&0x10000)!=(fontprev&0x10000))param+=(param.length()==0)?7:";7";
						if((font&0x01000)!=(fontprev&0x01000))param+=(param.length()==0)?7:";4";
						if((font&0x00100)!=(fontprev&0x00100))param+=(param.length()==0)?1:";1";
						if((font&0x000f0)!=(fontprev&0x000f0))param+=((param.length()==0)?"":";")+(30+((font>>4)&0xf));
						if((font&0x0000f)!=(fontprev&0x0000f))param+=((param.length()==0)?"":";")+(40+(font&0xf));
					}
					index=bytePushString((char)0x1B+"["+param+"m",vtbuf.buf,index);
					fontprev=font;
				}
				index=encodeUTF8(c,vtbuf.buf,index);
				if(c>=0x2E80){x++;}
			}
			if(y!=H-1){
				vtbuf.buf[index++]='\r';
				vtbuf.buf[index++]='\n';
			}
		}
		index=bytePushString((char)0x1B+"["+(cursorY+1)+";"+(cursorX+1)+"H",vtbuf.buf,index);
		index=bytePushString((char)0x1B+"["+((font&0xf00)>>8)+";"+(30+((font&0x0f0)>>4))+";"+(40+(font&0x00f))+"m",vtbuf.buf,index);
		index=bytePushString((char)0x1B+"[4"+(insertMode?'h':'l'),vtbuf.buf,index);
		index=bytePushString((char)0x1B+"["+(scrollStart+1)+";"+(scrollEnd+1)+"r",vtbuf.buf,index);
		vtbuf.version=bufferindex;
		vtbuf.read=index;
	}
	
	int readbufchar=0,readbuflen=0;
	void write(byte b){
		switch(readbuflen){
			case 0:
				if((b&0x80)==0)parse(b);
				else if((b&0x20)==0){readbuflen=1;readbufchar=b&0x1f;}
				else if((b&0x10)==0){readbuflen=2;readbufchar=b&0xf;}
				else if((b&0x8)==0){readbuflen=3;readbufchar=b&0x7;}
				else if((b&0x4)==0){readbuflen=4;readbufchar=b&0x3;}
				else if((b&0x2)==0){readbuflen=5;readbufchar=b&0x1;}
				return;
			case 1:
				parse((readbufchar<<6)|(b&0x3f));readbufchar=readbuflen=0;return;
			default:
				readbufchar=(readbufchar<<6)|(b&0x3f);readbuflen--;return;
		}
	}
	int escMode=0;
	int escLength=0;
	int escChar[]=new int[64];
	synchronized void parse(int c){
		bufferPush(c);
		switch(escMode){
			case 0:
				if(c==0x1B)escMode=1;
				else if(c<32)parseSpecial(c);
				else{put(c);if(c>=0x2E80)put(' ');}
				return;
			case 1:
				if(c=='[')escMode=2;
				else if(c=='(')escMode=3;
				else if(c==')')escMode=4;
				else{parseEscape(c);escMode=0;}
				return;
			case 2:
				if(('A'<=c&&c<='Z')||('a'<=c&&c<='z')){parseEscapeK(c);escMode=0;escLength=0;}
				else escChar[escLength++]=c;
				return;
			case 3:parseEscapeL(c);escMode=0;return;
			case 4:parseEscapeR(c);escMode=0;return;
		}
	}
	void parseSpecial(int c){
		switch(c){
			case 0x09:moveCursor((cursorX/8+1)*8,cursorY);return;
			case 0x08:moveCursor(cursorX-1,cursorY);return;
			case 0x0A:scrollCursor(cursorX,cursorY+1);return;
			case 0x0D:moveCursor(0,cursorY);return;
		}
	}
	int parseEscCharInt(){
		int num=0,i=0,pm=1;
		if(escChar[0]=='-'){pm=-1;i++;}
		else if(escChar[0]=='+')i++;
		for(;i<escLength;i++){
			int n=escChar[i];
			if('0'<=n&&n<='9')num=num*10+(n-'0');
			else return num;
		}
		return num;
	}
	void parseEscapeL(int c){/*System.out.println("ESC("+(char)c);*/}
	void parseEscapeR(int c){/*{System.out.println("ESC)"+(char)c);*/}
	void parseEscape(int c){/*System.out.println("ESC"+(char)c);*/switch(c){case 'M':scrollCursor(cursorX,cursorY-1);break;}}
	void parseEscapeK(int cmd){
		/*System.out.print("ESC[");for(int i=0;i<escLength;i++)System.out.print((char)escChar[i]);System.out.println((char)cmd);*/
		switch(cmd){
			case 'A':{
				if(escLength>0)scrollCursor(cursorX,cursorY-parseEscCharInt());
				else scrollCursor(cursorX,cursorY-1);
				return;
			}
			case 'B':{
				if(escLength>0)scrollCursor(cursorX,cursorY+parseEscCharInt());
				else scrollCursor(cursorX,cursorY+1);
				return;
			}
			case 'C':{
				if(escLength>0){moveCursor(cursorX+parseEscCharInt(),cursorY);}
				else moveCursor(cursorX+1,cursorY);
				return;
			}
			case 'D':{
				if(escLength>0)moveCursor(cursorX-parseEscCharInt(),cursorY);
				else moveCursor(cursorX-1,cursorY);
				return;
			}
			case 'H':case 'f':{
				if(escLength>0){
					int y=0,x=0,i=0,c;
					while(i<escLength&&'0'<=(c=escChar[i])&&c<='9'){y=y*10+(c-'0');i++;}
					if(i>=escLength||escChar[i++]!=';')return;
					while(i<escLength&&'0'<=(c=escChar[i])&&c<='9'){x=x*10+(c-'0');i++;}
					moveCursor(x-1,y-1);
					return;
				}else moveCursor(0,0);
				return;
			}
			case 'J':{
				if(escLength==1&&escChar[0]=='2'){
					moveCursor(0,0);
					for(int i=0;i<H;i++)line[i].length=0;
					return;
				}else if(escLength==1&&escChar[0]=='1'){
					for(int i=0;i<=cursorY;i++)line[i].length=0;
					moveCursor(0,0);
				}else{
					for(int i=cursorY;i<H;i++)line[i].length=0;
					cursorX=0;
				}
				return;
			}
			case 'K':{
				if(escLength==0){
					line[cursorY].length=cursorX;
					return;
				}
				return;
			}
			case 'L':{
				int num=escLength>0?parseEscCharInt():1;
				cursorX=0;
				for(int i=scrollEnd;i>=cursorY;i--)linetmp[i]=line[i];
				for(int i=scrollEnd;i>=cursorY;i--){
					if(i-num<cursorY){
						(line[i]=linetmp[i-num+scrollEnd-cursorY+1]).length=0;
					}else line[i]=linetmp[i-num];
				}
				return;
			}
			case 'M':{
				int num=escLength>0?parseEscCharInt():1;
				for(int i=cursorY;i<=scrollEnd;i++)linetmp[i]=line[i];
				for(int i=cursorY;i<=scrollEnd;i++){
					if(i+num>scrollEnd)(line[i]=linetmp[i+num+cursorY-scrollEnd-1]).length=0;
					else line[i]=linetmp[i+num];
				}
				return;
			}
			case 'P':{
				int num=escLength>0?parseEscCharInt():1;
				Line ln=line[cursorY];
				for(int i=cursorX;i<ln.length-num;i++){
					ln.chars[i]=ln.chars[i+num];
					ln.fonts[i]=ln.fonts[i+num];
				}
				ln.length-=num;
				return;
			}
			case 'h':case 'l':{
				boolean flag=(cmd=='h');
				if(escLength==1){
					switch(escChar[0]){
						case '4':insertMode=flag;return;
					}
				}
				return;
			}
			case 'm':{
				if(escLength==0){font=fontDefault;return;}
				int n0=0,n1=0;
				
				for(int i=0;i<escLength;i++){
					int c=escChar[i];
					if('0'<=c&&c<='9'){n0=n1;n1=(c-'0');}
					if(i+1==escLength||escChar[i+1]==';'){
						if(n0==0){
							if(n1==0){font=fontDefault;continue;}
							else if(n1==1)font|=0x00100;
							else if(n1==4)font|=0x01000;
							else if(n1==7)font|=0x10000;
						}else if(n0==3){
							if(n1<8)font=(font&0x1110f)|(n1<<4);
						}else if(n0==4){
							if(n1<8)font=(font&0x111f0)|n1;
						}
						n0=n1=0;
					}
				}
				return;
			}
			case 'r':{
				int ss=0,se=0,i=0,c;
				while(i<escLength&&'0'<=(c=escChar[i])&&c<='9'){ss=ss*10+(c-'0');i++;}
				if(i>=escLength||escChar[i++]!=';')return;
				while(i<escLength&&'0'<=(c=escChar[i])&&c<='9'){se=se*10+(c-'0');i++;}
				scrollStart=ss-1;
				scrollEnd=se-1;
				return;
			}
		}
	}
	void put(int c){
		if(cursorX>=W)scrollCursor(0,cursorY+1);
		Line ln=line[cursorY];
		if(insertMode){
			for(int i=ln.length;i>cursorX;i--){ln.chars[i]=ln.chars[i-1];ln.fonts[i]=ln.fonts[i-1];}
			ln.chars[cursorX]=c;
			ln.fonts[cursorX]=font;
			ln.length++;
			cursorX++;
		}else{
			ln.chars[cursorX]=c;
			ln.fonts[cursorX]=font;
			if(cursorX==ln.length)ln.length++;
			cursorX++;
		}
	}
	void moveCursor(int x,int y){
		Line ln=line[y];
		int xn=x-ln.length;
		for(int i=0;i<xn;i++){ln.chars[ln.length]=' ';ln.fonts[ln.length]=fontDefault;ln.length++;}
		cursorX=x;
		cursorY=y;
	}
	void scrollCursor(int x,int y){
		if(cursorY<scrollStart||cursorY>scrollEnd){moveCursor(x,y);return;}
		if(y<scrollStart){
			int n=scrollStart-y;
			for(int i=scrollEnd;i>=scrollStart;i--)linetmp[i]=line[i];
			if(scrollEnd-n+1<0)for(int i=scrollEnd;i>=scrollStart;i--)line[i].length=0;else
			for(int i=scrollEnd;i>=scrollStart;i--){
				if(i-n<scrollStart)(line[i]=linetmp[i-n+1+scrollEnd-scrollStart]).length=0;
				else line[i]=linetmp[i-n];
			}
			y=scrollStart;
		}else if(y>scrollEnd){
			int n=y-scrollEnd;
			for(int i=scrollStart;i<=scrollEnd;i++)linetmp[i]=line[i];
			for(int i=scrollStart;i<=scrollEnd;i++){
				if(i+n>scrollEnd)(line[i]=linetmp[i+n-1-scrollEnd+scrollStart]).length=0;
				else line[i]=linetmp[i+n];
			}
			y=scrollEnd;
		}
		Line ln=line[y];
		int xn=x-ln.length;
		for(int i=0;i<xn;i++){ln.chars[ln.length]=' ';ln.fonts[ln.length]=fontDefault;ln.length++;}
		cursorX=x;
		cursorY=y;
	}
}
