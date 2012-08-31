


function Comet(url){
	var http=new XMLHttpRequest();
	http.open("POST",url,true);
	setTimeout(function(){http.send("+");},10);
	var comet=this;
	http.onreadystatechange=function(){
		if(http.readyState!=4)return;
		if(http.status!=200||http.responseText.length!=16){
			comet.onclose();
		}
		comet.id=http.responseText;
		comet.onopen();
		recv();
	}
	function serverClosed(){
		if(comet.closed)return;
		comet.closed=true;
		comet.onclose();
	}
	function recv(){
		var http=new XMLHttpRequest();
		http.open("POST",url,true);
		http.send("<"+comet.id);
		http.onreadystatechange=function(){
			if(http.readyState!=4)return;
			if(http.status!=200){comet.close();return;}
			var str=http.responseText;
			var index=0;
			while(index<str.length){
				var len=parseInt(str.substr(index,8),16);
				if(len==0xffffffff){serverClosed();return;}
				var msg=str.substr(index+8,len);
				if(msg.length!=len){
					comet.close();return;
				}
				comet.onmessage({data:msg});
				index+=8+len;
			}
			recv();
		}
	}
	var sendQueue=[];
	var sending=false;
	function hexLength(x){
		var s="";
		for(var i=0;i<8;i++)s+="0123456789abcdef".charAt((x>>(4*(8-i-1)))&0xf);
		return s;
	}
	function send(){
		sending=true;
		var http=new XMLHttpRequest();
		http.open("POST",url,true);
		if(sendQueue==null){http.send("-"+comet.id);return;}
		
		var data="";
		for(var i=0;i<sendQueue.length;i++)data+=hexLength(sendQueue[i].length)+sendQueue[i];
		sendQueue=[];
		http.send(">"+comet.id+data);
		http.onreadystatechange=function(){
			if(http.readyState!=4)return;
			if(http.status!=200){
				comet.close();
				return;
			}
			sending=false;
			if(sendQueue==null||sendQueue.length)send();
		}
	}
	this._sendMsg=function(msg){
		sendQueue.push(msg);
		if(!sending)send();
	}
	this._sendClose=function(){
		sendQueue=null;
		if(!sending)send();
	}
}

Comet.prototype.onopen=function(){};
Comet.prototype.onclose=function(){};
Comet.prototype.onmessage=function(){};
Comet.prototype.close=function(){
	if(this.closed)return;
	this.closed=true;
	this._sendClose();
	this.onclose();	
};
Comet.prototype.send=function(msg){
	if(this.closed)return;
	this._sendMsg(msg);
};
