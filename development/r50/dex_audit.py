"""Read DEX identities and check direct references against the actual pinned host."""
import struct,hashlib,zlib

class Dex:
    def __init__(self,data):
        self.data=data
        assert data[:4]==b'dex\n'
        assert hashlib.sha1(data[32:]).digest()==data[12:32]
        assert zlib.adler32(data[12:])&0xffffffff==self.u4(8)
        assert self.u4(32)==len(data)
        self.strings=self.u4(60);self.types=self.u4(68);self.protos=self.u4(76);self.methods=self.u4(92)
        self.class_count=self.u4(96);self.class_off=self.u4(100);self.string_cache={}
    def u4(self,p):return struct.unpack_from('<I',self.data,p)[0]
    def u2(self,p):return struct.unpack_from('<H',self.data,p)[0]
    def leb(self,p):
        value=0;shift=0
        while True:
            b=self.data[p];p+=1;value|=(b&127)<<shift
            if b<128:return value,p
            shift+=7
    def string(self,index):
        if index not in self.string_cache:
            p=self.u4(self.strings+4*index);_,p=self.leb(p);end=self.data.index(b'\0',p)
            self.string_cache[index]=self.data[p:end].decode('utf8','surrogatepass')
        return self.string_cache[index]
    def type(self,index):return self.string(self.u4(self.types+4*index))
    def types_at(self,p):return [] if not p else [self.type(self.u2(p+4+2*i)) for i in range(self.u4(p))]
    def class_rows(self):
        for i in range(self.class_count):
            p=self.class_off+32*i;parent=self.u4(p+8)
            yield self.type(self.u4(p)),(None if parent==0xffffffff else self.type(parent)),self.types_at(self.u4(p+12)),self.u4(p+24)
    def classes(self):return [c for c,_,_,_ in self.class_rows()]
    def method(self,index):
        p=self.methods+8*index;owner=self.type(self.u2(p));proto=self.protos+12*self.u2(p+2)
        return owner,self.string(self.u4(p+4))+'('+''.join(self.types_at(self.u4(proto+8)))+')'+self.type(self.u4(proto+4))
    def references(self):return [self.method(i) for i in range(self.u4(88))]
    def defined(self,p):
        if not p:return []
        counts=[]
        for _ in range(4):n,p=self.leb(p);counts.append(n)
        for _ in range(counts[0]+counts[1]):_,p=self.leb(p);_,p=self.leb(p)
        result=[]
        for count in counts[2:]:
            index=0
            for _ in range(count):
                diff,p=self.leb(p);index+=diff;_,p=self.leb(p);_,p=self.leb(p);result.append(self.method(index))
        return result

def audit_references(old_zip,new_dex):
    import re
    wanted=set(owner for owner,_ in Dex(new_dex).references());parents={};defined={}
    for name in old_zip.namelist():
        if not re.fullmatch(r'classes\d*\.dex',name):continue
        dex=Dex(old_zip.read(name))
        for owner,parent,interfaces,offset in dex.class_rows():
            parents[owner]=([parent] if parent else [])+interfaces
            # Ancestor methods can be required by an inherited static facade.
            defined[owner]=set(signature for _,signature in dex.defined(offset))
    def resolves(owner,signature,seen):
        if owner in seen:return False
        seen.add(owner)
        if signature in defined.get(owner,set()):return True
        return any(resolves(parent,signature,seen) for parent in parents.get(owner,[]))
    checked=[];missing=[]
    for owner,signature in Dex(new_dex).references():
        if owner in parents:
            (checked if resolves(owner,signature,set()) else missing).append(owner+'->'+signature)
    assert not missing,'Unresolved host method references: '+str(missing)
    return {'verified':True,'checked_host_method_references':sorted(set(checked))}
