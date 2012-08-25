function VT100(w,h){
	this.W=w;this.H=h;
	this.line=[];
	this.linetmp=[];
	for(var i=0;i<this.H;i++)this.line[i]=new VT100.Line(this.W);
	this.font=this.fontDefault=0x00088;
	this.scrollStart=0;this.scrollEnd=this.H-1;
	this.cursorX=0;this.cursorY=0;
	this.insertMode=false;
	this.escMode=0;
	this.escChar='';
}
VT100.Line=function(size){
	this.length=0;
	this.chars=new Array(size);
	this.fonts=new Array(size);
};
VT100.prototype.write=function(c){
	switch(this.escMode){
		case 0:
			if(c=='\x1B')this.escMode=1;
			else if(c<'\x20')this.parseSpecial(c.charCodeAt(0));
			else{this.put(c);if(c.charCodeAt(0)>=0x2E80)this.put('');}
			return;
		case 1:
			if(c=='[')this.escMode=2;
			else if(c=='(')this.escMode=3;
			else if(c==')')this.escMode=4;
			else{this.parseEscape(c);this.escMode=0;}
			return;
		case 2:
			if(('A'<=c&&c<='Z')||('a'<=c&&c<='z')){this.parseEscapeK(c);this.escMode=0;this.escChar='';}
			else this.escChar+=c;
			return;
		case 3:this.parseEscapeL(c);this.escMode=0;return;
		case 4:this.parseEscapeR(c);this.escMode=0;return;
	}
}
VT100.prototype.parseSpecial=function(c){
	switch(c){
		case 0x08:this.moveCursor(this.cursorX-1,this.cursorY);return;
		case 0x0A:this.scrollCursor(this.cursorX,this.cursorY+1);return;
		case 0x0D:this.moveCursor(0,this.cursorY);return;
		case 0x07:if(this.onBell)this.onBell();return;
	}
}
VT100.prototype.parseEscapeL=function(c){}
VT100.prototype.parseEscapeR=function(c){}
VT100.prototype.parseEscape=function(c){switch(c){case 'M':this.scrollCursor(this.cursorX,this.cursorY-1);break;}}
VT100.prototype.parseEscapeK=function(cmd){
	switch(cmd){
		case 'A':{
			if(this.escChar)this.scrollCursor(this.cursorX,this.cursorY-parseInt(this.escChar));
			else this.scrollCursor(this.cursorX,this.cursorY-1);
			return;
		}
		case 'B':{
			if(this.escChar)this.scrollCursor(this.cursorX,this.cursorY+parseInt(this.escChar));
			else this.scrollCursor(this.cursorX,this.cursorY+1);
			return;
		}
		case 'C':{
			if(this.escChar){this.moveCursor(this.cursorX+parseInt(this.escChar),this.cursorY);}
			else this.moveCursor(this.cursorX+1,this.cursorY);
			return;
		}
		case 'D':{
			if(this.escChar>0)this.moveCursor(this.cursorX-parseInt(this.escChar),this.cursorY);
			else this.moveCursor(this.cursorX-1,this.cursorY);
			return;
		}
		case 'H':case 'f':{
			if(this.escChar){
				var yx=this.escChar.split(";");
				this.moveCursor(parseInt(yx[1])-1,parseInt(yx[0])-1);
				return;
			}else this.moveCursor(0,0);
			return;
		}
		case 'J':{
			if(this.escChar=='2'){
				this.moveCursor(0,0);
				for(var i=0;i<this.H;i++)this.line[i].length=0;
				return;
			}else if(this.escChar=='1'){
				for(var i=0;i<=this.cursorY;i++)this.line[i].length=0;
				this.moveCursor(0,0);
			}else{
				for(var i=this.cursorY;i<this.H;i++)this.line[i].length=0;
				this.cursorX=0;
			}
			return;
		}
		case 'K':{
			if(!this.escChar){
				this.line[this.cursorY].length=this.cursorX;
				return;
			}
			return;
		}
		case 'L':{
			var num=this.escChar?parseInt(this.escChar):1;
			this.cursorX=0;
			for(var i=this.scrollEnd;i>=this.cursorY;i--)this.linetmp[i]=this.line[i];
			for(var i=this.scrollEnd;i>=this.cursorY;i--){
				if(i-num<this.cursorY){
					(this.line[i]=this.linetmp[i-num+this.scrollEnd-this.cursorY+1]).length=0;
				}else this.line[i]=this.linetmp[i-num];
			}
			return;
		}
		case 'M':{
			var num=this.escChar?parseInt(this.escChar):1;
			for(var i=this.cursorY;i<=this.scrollEnd;i++)this.linetmp[i]=this.line[i];
			for(var i=this.cursorY;i<=this.scrollEnd;i++){
				if(i+num>this.scrollEnd)(this.line[i]=this.linetmp[i+num+this.cursorY-this.scrollEnd-1]).length=0;
				else this.line[i]=this.linetmp[i+num];
			}
			return;
		}
		case 'P':{
			var num=this.escChar?parseInt(this.escChar):1;
			var ln=this.line[this.cursorY];
			for(var i=this.cursorX;i<ln.length-num;i++){
				ln.chars[i]=ln.chars[i+num];
				ln.fonts[i]=ln.fonts[i+num];
			}
			ln.length-=num;
			return;
		}
		case 'h':case 'l':{
			var flag=(cmd=='h');
			switch(this.escChar){
				case '4':this.insertMode=flag;return;
			}
			return;
		}
		case 'm':{
			if(!this.escChar){this.font=this.fontDefault;return;}
			var params=this.escChar.split(";");
			for(var i=0;i<params.length;i++){
				var val=params[i]%10;
				var key=(params[i]-val)/10;
				if(key==0){
					if(val==0){this.font=this.fontDefault;continue;}
					else if(val==1)this.font|=0x00100;
					else if(val==4)this.font|=0x01000;
					else if(val==7)this.font|=0x10000;
				}else if(key==3){
					if(val<8)this.font=(this.font&0x1110f)|(val<<4);
				}else if(key==4){
					if(val<8)this.font=(this.font&0x111f0)|val;
				}
			}
			return;
		}
		case 'r':{
			var se=this.escChar.split(";");
			this.scrollStart=parseInt(se[0])-1;
			this.scrollEnd=parseInt(se[1])-1;
			return;
		}
	}
}
VT100.prototype.put=function(c){
	if(this.cursorX>=this.W)this.scrollCursor(0,this.cursorY+1);
	var ln=this.line[this.cursorY];
	if(this.insertMode){
		for(var i=ln.length;i>this.cursorX;i--){ln.chars[i]=ln.chars[i-1];ln.fonts[i]=ln.fonts[i-1];}
		ln.chars[this.cursorX]=c;
		ln.fonts[this.cursorX]=this.font;
		ln.length++;
		this.cursorX++;
	}else{
		ln.chars[this.cursorX]=c;
		ln.fonts[this.cursorX]=this.font;
		if(this.cursorX==ln.length)ln.length++;
		this.cursorX++;
	}
}
VT100.prototype.moveCursor=function(x,y){
	var ln=this.line[y];
	if(ln){
		var xn=x-ln.length;
		for(var i=0;i<xn;i++){ln.chars[ln.length]=' ';ln.fonts[ln.length]=this.fontDefault;ln.length++;}
	}
	this.cursorX=x;
	this.cursorY=y;
}
VT100.prototype.scrollCursor=function(x,y){
	if(this.cursorY<this.scrollStart||this.cursorY>this.scrollEnd){this.moveCursor(x,y);return;}
	if(y<this.scrollStart){
		var n=this.scrollStart-y;
		for(var i=this.scrollEnd;i>=this.scrollStart;i--)this.linetmp[i]=this.line[i];
		if(this.scrollEnd-n+1<0)for(var i=this.scrollEnd;i>=this.scrollStart;i--)this.line[i].length=0;else
		for(var i=this.scrollEnd;i>=this.scrollStart;i--){
			if(i-n<this.scrollStart)(this.line[i]=this.linetmp[i-n+1+this.scrollEnd-this.scrollStart]).length=0;
			else this.line[i]=this.linetmp[i-n];
		}
		y=this.scrollStart;
	}else if(y>this.scrollEnd){
		var n=y-this.scrollEnd;
		for(var i=this.scrollStart;i<=this.scrollEnd;i++)this.linetmp[i]=this.line[i];
		for(var i=this.scrollStart;i<=this.scrollEnd;i++){
			if(i+n>this.scrollEnd)(this.line[i]=this.linetmp[i+n-1-this.scrollEnd+this.scrollStart]).length=0;
			else this.line[i]=this.linetmp[i+n];
		}
		y=this.scrollEnd;
	}
	var ln=this.line[y];
	if(ln){
		var xn=x-ln.length;
		for(var i=0;i<xn;i++){ln.chars[ln.length]=' ';ln.fonts[ln.length]=this.fontDefault;ln.length++;}
	}
	this.cursorX=x;
	this.cursorY=y;
}





function Terminal(id,w,h,color){
	var div=this.main=document.getElementById(id);
	div.innerHTML="";
	var pre=this.text=document.createElement("PRE");
	var cur=this.cursor=document.createElement("DIV");
	div.style.overflow="hidden";
	pre.style.userSelect=pre.style.WebkitUserSelect=pre.style.KhtmlUserSelect="text";
	pre.style.fontFamily="Osaka-Mono,MS-Gothic,MS-Mincho,SimSun,monospace";
	pre.style.lineHeight="1em";
	pre.style.display="inline";
	cur.style.position="absolute";cur.style.opacity=cur.style.MozOpacity=0.5;
	var front=document.createElement("DIV");
	front.style.userSelect=front.style.WebkitUserSelect=front.style.KhtmlUserSelect="none";
	front.style.position="relative";front.style.left=front.style.top=front.style.width=front.style.height=0;
	front.appendChild(cur);
	div.appendChild(front);
	div.appendChild(pre);
	var obj=this;
	this.vt100=new VT100(w,h);
	this.vt100.onBell=function(){if(obj.onBell)obj.onBell();}
	this.W=w;this.H=h;
	this.calcSize();
	this.setColor(color?color:Terminal.defaultColorList[0]);
}
Terminal.prototype.resize=function(w,h){
	this.W=this.vt100.W=w;
	this.H=this.vt100.H=h;
	for(var i=0;i<h;i++)if(!this.vt100.line[i])this.vt100.line[i]=new VT100.Line(w);
	this.calcSize();
}
Terminal.prototype.write=function(c){this.vt100.write(c);}
Terminal.prototype.setColor=function(color){
	this.color=color;
	this.main.style.background=color.background;
	this.cursor.style.background=color.cursor;
	if(color.backgroundColor)this.main.style.backgroundColor=color.backgroundColor;
	this.updateView();
}
Terminal.prototype.calcSize=function(){
	this.text.innerHTML="<div><span>_</span></div>";
	this.char_w=this.text.firstChild.firstChild.offsetWidth;
	this.char_h=this.text.firstChild.offsetHeight;
	this.cursor.style.width=this.char_w+"px";
	this.cursor.style.height=this.char_h+"px";
	this.main.style.width=this.char_w*this.W+"px";
	this.main.style.height=this.char_h*this.H+"px";
	this.text.innerHTML="";
};
Terminal.prototype.setSpanFont=function(span,font){
	var highlight=font&0x00100;
	var flip=font&0x10000;
	if(font&0x01000)span.style.textDecoration="underline";
	var fg=(highlight?this.color.highlight:this.color.normal)[(font&0x000f0)>>4];
	var bg=(highlight?this.color.highlight:this.color.normal)[font&0x0000f];
	if(flip){
		span.style.color=bg?bg:this.color.backgroundColor||this.color.background;
		span.style.background=fg?fg:highlight?this.color.emphasis:this.color.foreground;
	}else{
		span.style.color=fg?fg:highlight?this.color.emphasis:this.color.foreground;
		span.style.background=bg?bg:null;
	}
};
Terminal.prototype.createHalfChar=function(s){
	var span=document.createElement("SPAN");
	span.textContent=s;
	var w2=Math.floor(this.char_w/1);
	span.style.marginRight=-w2+"px";
	span.style.marginLeft=-(this.char_w-w2)+"px";
	return span;
}
Terminal.prototype.updateView=function(){
	this.text.innerHTML="";
	for(var i=0;i<this.vt100.H;i++){
		var s="";
		var div=document.createElement("SPAN");div.style.display="block";
		div.style.height=this.char_h+"px";
		var fontprev=-1;
		var specialhalfprev=-1;
		var line=this.vt100.line[i];
		for(var j=0;j<line.length;j++){
			var font=line.fonts[j];if(fontprev<0)fontprev=font;
			var c=line.chars[j];
			var cc=c.charCodeAt(0);
			var specialhalf=(cc>=0x80&&cc<0x2E80);
			if(font==fontprev&&specialhalf==specialhalfprev){
				s+=c;
			}else{
				if(s){
					var span=document.createElement("SPAN");
					if(specialhalfprev){
						for(var k=0;k<s.length;k++)span.appendChild(this.createHalfChar(s.charAt(k)));
					}else span.textContent=s;
					this.setSpanFont(span,fontprev);
					div.appendChild(span);
				}
				s=c;
				fontprev=font;
				specialhalfprev=specialhalf;
			}
		}
					var span=document.createElement("SPAN");
					if(specialhalfprev){
						for(var k=0;k<s.length;k++)span.appendChild(this.createHalfChar(s.charAt(k)));
					}else span.textContent=s;
					this.setSpanFont(span,fontprev);
					div.appendChild(span);
		div.appendChild(document.createElement("BR"));
		this.text.appendChild(div);
	}
	this.cursor.style.left=this.char_w*this.vt100.cursorX+"px";
	this.cursor.style.top=this.char_h*this.vt100.cursorY+"px";
};
Terminal.defaultColorList=[
	{
		name:"white",
		normal:["#000","#F00","#0F0","#AA0","#00F","#F0F","#0AA","white"],
		highlight:["#666","#F60","#0F6","#AF0","#60F","#F0A","#06A","white"],
		foreground:"black",background:"white",emphasis:"#600",cursor:"#00F"
	},
	{
		name:"black",
		normal:["#FFF","#F66","#4F4","#FF0","#88F","#F0F","#0FF","black"],
		highlight:["#AAA","#F00","#6F6","#AA0","#66F","#F6F","#6FF","black"],
		foreground:"white",background:"black",emphasis:"#FAA",cursor:"#CCF"
	},
	{
		name:"novel",
		normal:["#000000","#990000","#00A600","#999900","#0000B3","#B300B3","#00A6B3","#BFBFBF"],
		highlight:["#000000","#990000","#00A600","#999900","#0000B3","#B300B3","#00A6B3","#BFBFBF"],
		foreground:"#532D2C",background:"#DFDBC3",emphasis:"#A1320B",cursor:"#000000"
	},
	{
		name:"green",
		normal:["#000000","#990000","#00A600","#999900","#0000B3","#B300B3","#00A6B3","#BFBFBF"],
		highlight:["#000000","#990000","#00A600","#999900","#0000B3","#B300B3","#00A6B3","#BFBFBF"],
		foreground:"#BFFFBF",background:"#001F00",emphasis:"#7FFF7F",cursor:"#FFFFFF"
	},
	{
		name:"icon",
		normal:["#000000","#990000","#00A600","#999900","#0000B3","#B300B3","#00A6B3","#BFBFBF"],
		highlight:["#000000","#990000","#00A600","#999900","#0000B3","#B300B3","#00A6B3","#BFBFBF"],
		foreground:"#000000",background:"url(/favicon.ico)",backgroundColor:"white",emphasis:"#333333",cursor:"#000000"
	}
];