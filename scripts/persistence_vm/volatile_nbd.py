#!/usr/bin/env python3
"""Single-connection test NBD: write-back sectors; FLUSH prefix and per-request FUA persist."""
import json
import os
import socket
import struct
import threading

REQUEST=0x25609513
REPLY=0x67446698
MAX_REQUEST=32*1024*1024


def receive(connection,size):
    chunks=[]
    while size:
        data=connection.recv(size)
        if not data:raise EOFError()
        chunks.append(data);size-=len(data)
    return b''.join(chunks)


class VolatileNbd:
    def __init__(self,image,socket_path,ledger,honour_barriers=True):
        self.image=image;self.socket_path=socket_path;self.honour_barriers=honour_barriers
        self.fd=os.open(image,os.O_RDWR);self.size=os.fstat(self.fd).st_size
        self.dirty={};self.stats=dict(reads=0,writes=0,flushes=0,fua=0,dropped_sectors=0)
        self.errors=[];self.cutting=False;self.peer=None
        self.log=open(ledger,'w',buffering=1);self.sequence=0
        self.listener=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
        self.listener.bind(str(socket_path));self.listener.listen(1);self.listener.settimeout(1)
        self.thread=threading.Thread(target=self.serve,daemon=True);self.thread.start()

    def event(self,kind,**values):
        self.sequence+=1;self.log.write(json.dumps(dict(sequence=self.sequence,event=kind,**values))+'\n')

    def persist(self,sectors):
        for number in sorted(sectors):
            value=self.dirty[number];position=number*512
            while value:
                written=os.pwrite(self.fd,value,position)
                if not written:raise OSError('Short stable-image write')
                position+=written;value=value[written:]
        os.fsync(self.fd)
        for number in sectors:del self.dirty[number]

    def serve(self):
        try:
            while not self.cutting:
                try:self.peer,_=self.listener.accept();break
                except socket.timeout:continue
            if self.peer is None:return
            with self.peer as connection:
                # Oldstyle export: HAS_FLAGS | SEND_FLUSH | SEND_FUA; no multi-connection claim.
                connection.sendall(struct.pack('>8sQQI',b'NBDMAGIC',0x420281861253,self.size,13)+bytes(124))
                while not self.cutting:
                    magic,flags,command,cookie,offset,length=struct.unpack('>IHH8sQI',receive(connection,28))
                    if self.cutting:return
                    if magic!=REQUEST:raise ValueError('Invalid NBD request magic')
                    if command==2:
                        self.event('disconnect',dirty=len(self.dirty));return
                    payload=receive(connection,length) if command==1 else b''
                    if self.cutting:return
                    error=0;response=b''
                    if flags&~1 or command not in (0,1,3):error=22
                    elif command==3 and (offset or length):error=22
                    elif command!=3 and (length<=0 or length>MAX_REQUEST or offset%512 or length%512 or offset+length>self.size):error=22
                    if not error:
                        if command==0:
                            self.stats['reads']+=1
                            response=os.pread(self.fd,length,offset)
                            if len(response)!=length:raise OSError('Short stable-image read')
                            data=bytearray(response)
                            for index in range(length//512):
                                number=offset//512+index
                                if number in self.dirty:data[index*512:(index+1)*512]=self.dirty[number]
                            response=bytes(data)
                        elif command==1:
                            self.stats['writes']+=1
                            sectors=list(range(offset//512,(offset+length)//512))
                            for index,number in enumerate(sectors):self.dirty[number]=payload[index*512:(index+1)*512]
                            if flags&1:
                                if self.honour_barriers:self.persist(sectors)
                                self.stats['fua']+=1
                            self.event('write',offset=offset,length=length,fua=bool(flags&1),dirty=len(self.dirty))
                        else:
                            count=len(self.dirty)
                            if self.honour_barriers:self.persist(list(self.dirty))
                            self.stats['flushes']+=1
                            self.event('flush',persisted_sectors=count if self.honour_barriers else 0,honoured=self.honour_barriers)
                    else:self.event('rejected',command=command,flags=flags,offset=offset,length=length)
                    connection.sendall(struct.pack('>II8s',REPLY,error,cookie)+response)
        except (EOFError,BrokenPipeError,ConnectionResetError,OSError) as failure:
            if not self.cutting and not isinstance(failure,EOFError):self.errors.append(repr(failure))
        except Exception as failure:
            self.errors.append(repr(failure))

    def arm_cut(self):self.cutting=True

    def stop(self):
        self.cutting=True
        if self.peer:
            try:self.peer.shutdown(socket.SHUT_RDWR)
            except OSError:pass
        self.listener.close();self.thread.join(timeout=10)
        if self.thread.is_alive():raise RuntimeError('NBD worker failed to stop')
        self.stats['dropped_sectors']=len(self.dirty)
        self.event('power_cut',dropped_sectors=len(self.dirty));self.dirty.clear()
        self.log.close();os.close(self.fd);self.socket_path.unlink(missing_ok=True)
        if self.errors:raise RuntimeError('NBD acquisition failed: '+str(self.errors))
        return dict(self.stats)
