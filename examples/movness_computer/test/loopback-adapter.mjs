// TEST ONLY: this cloud sandbox denies OS adapter enumeration. Limit the integration
// test to loopback; no production code or real LAN reachability is substituted.
import os from 'node:os';
import { syncBuiltinESMExports } from 'node:module';
os.networkInterfaces = () => ({ fixture: [{address:'127.0.0.1',netmask:'255.0.0.0',family:'IPv4',mac:'00:00:00:00:00:00',internal:false,cidr:'127.0.0.1/8'}] });
syncBuiltinESMExports();
