// Direct protocol777 fixture; no official rendering client.
import net from 'node:net';import zlib from 'node:zlib';import crypto from 'node:crypto';import {EventEmitter} from 'node:events';
const port=Number(process.argv[2]);
const viewDistance=Number(process.argv[3]??3);
if(!Number.isInteger(viewDistance)||viewDistance<3||viewDistance>32)throw Error('Invalid fixture view distance');
const vi = value => { const out = []; do { let byte = value & 127; value >>>= 7; if (value) byte |= 128; out.push(byte); } while (value); return Buffer.from(out); };
const readVi = (buffer, start = 0) => { let value = 0; for (let n = 0; n < 5; n++) { if (start + n >= buffer.length) return null; const byte = buffer[start + n]; value |= (byte & 127) << (7 * n); if (!(byte & 128)) return { value, end: start + n + 1 }; } throw Error('Invalid VarInt'); };
const string = value => { const bytes = Buffer.from(value); return Buffer.concat([vi(bytes.length), bytes]); };
const uuidString = raw => { const s = raw.toString('hex'); return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`; };
class Client extends EventEmitter {
  constructor(name) {
    super(); this.name = name; this.phase = 'login'; this.buffer = Buffer.alloc(0); this.compression = -1; this.entities = new Map();
    this.socket = net.createConnection({ host: '127.0.0.1', port });
    this.socket.on('connect', () => { const p = Buffer.alloc(2); p.writeUInt16BE(port); this.send(0, Buffer.concat([vi(777), string('localhost'), p, vi(2)])); this.send(0, Buffer.concat([string(name), crypto.randomBytes(16)])); });
    this.socket.on('error', error => { this.failure = error; });
    this.socket.on('close', () => { clearInterval(this.tick); this.closed = true; });
    this.socket.on('data', chunk => {
      try {
        this.buffer = Buffer.concat([this.buffer, chunk]);
        while (this.buffer.length) {
          const length = readVi(this.buffer); if (!length || this.buffer.length < length.end + length.value) break;
          let body = this.buffer.subarray(length.end, length.end + length.value); this.buffer = this.buffer.subarray(length.end + length.value);
          if (this.compression >= 0) { const size = readVi(body); body = size.value ? zlib.inflateSync(body.subarray(size.end)) : body.subarray(size.end); }
          const id = readVi(body); this.receive(id.value, body.subarray(id.end));
        }
      } catch (error) { this.failure = error; this.socket.destroy(); }
    });
  }
  send(id, body = Buffer.alloc(0)) {
    if (this.closed) return;
    let packet = Buffer.concat([vi(id), body]);
    if (this.compression >= 0) packet = packet.length >= this.compression ? Buffer.concat([vi(packet.length), zlib.deflateSync(packet)]) : Buffer.concat([vi(0), packet]);
    this.socket.write(Buffer.concat([vi(packet.length), packet]));
  }
  receive(id, body) {
    if (this.phase === 'login') {
      if (id === 0) this.failure = Error(`Login disconnect: ${body.toString('hex')}`);
      if (id === 3) this.compression = readVi(body).value;
      if (id === 2) { this.uuid = uuidString(body.subarray(0, 16)); this.send(3); this.phase = 'configuration'; this.send(0, Buffer.concat([string('en_us'), Buffer.from([viewDistance]), vi(0), Buffer.from([1, 127]), vi(1), Buffer.from([0, 1]), vi(2)])); }
    } else if (this.phase === 'configuration') {
      if (id === 4) this.send(4, body);
      if (id === 5) this.send(5, body);
      if (id === 15) this.send(7, vi(0));
      if (id === 20) this.send(9);
      if (id === 3) { this.send(3); this.phase = 'play'; this.tick = setInterval(() => { this.send(0x21, Buffer.from([1])); this.send(0x0d); }, 50); }
    } else {
      if (id === 0x2d) this.send(0x1c, body);
      if (id === 0x49) { const token = readVi(body); this.send(0, Buffer.concat([vi(token.value), body.subarray(token.end, token.end + 24), body.subarray(token.end + 48, token.end + 56)])); this.send(0x2c); }
      if (id === 0x32) { this.send(0x2c); this.joined = true; }
      if (id === 0x01) { const entity = readVi(body); this.entities.set(uuidString(body.subarray(entity.end, entity.end + 16)), entity.value); }
      if (id === 0x20) this.failure = Error(`Play disconnect: ${body.toString('hex')}`);
    }
  }
  input(flags) { this.send(0x2b, Buffer.from([flags])); }
  look(yaw, pitch = 0) { const body = Buffer.alloc(9); body.writeFloatBE(yaw); body.writeFloatBE(pitch, 4); body[8] = 1; this.send(0x20, body); }
  interact(id, sneak = false, hand = 0) {
    // Protocol777 uses hand + packed Vec3.ZERO + secondary-action, without an action enum.
    this.send(0x1a, Buffer.concat([vi(id), vi(hand), Buffer.from([0, sneak ? 1 : 0])]));
  }
  close() { clearInterval(this.tick); this.socket.destroy(); }
}

const client=new Client('SceneNative');
setInterval(()=>{if(client.failure){console.error(client.failure);process.exit(1);}if(client.joined&&!client.reported){client.reported=true;console.log('JOINED');}},100);
let input='';process.stdin.setEncoding('utf8');process.stdin.on('data',chunk=>{input+=chunk;let end;while((end=input.indexOf('\n'))>=0){const line=input.slice(0,end);input=input.slice(end+1);if(!line)continue;const request=JSON.parse(line);if(request.action==='interact'){const deadline=Date.now()+10000;const send=()=>{if(client.entities.has(request.uuid)){client.interact(client.entities.get(request.uuid));console.log('INTERACT_SENT');}else if(Date.now()<deadline)setTimeout(send,50);else{console.error('Target entity not received');process.exit(1)}};send();}}});
process.on('SIGTERM',()=>{client.close();process.exit(0)});
