package vt100;
public class VTBuf{
	public VTBuf(int offset,int size){this.offset=offset;length=size;buf=new byte[offset+size];}
	public int offset,length;
	public byte buf[];
	public Terminal terminal=null;
	public long version=0;
	public int read=0;
}
