const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const reads=new Set(['snapshot','screenshot','node_details']);
/** A virtual display can briefly withdraw its windows while resuming. Retry reads only. */
export async function recoverRead(command,op,args){
 const until=Date.now()+1500;
 while(true){try{return await command(op,args);}catch(error){
  if(!reads.has(op)||!/Aucune fenêtre|L’app demandée n’est pas visible|Fenêtre IA temporairement indisponible|No accessible window/.test(error.message)||Date.now()>=until)throw error;
  await pause(80);
 }}
}
