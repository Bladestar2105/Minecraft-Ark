package de.ark.jurassic;

public final class JobRulesTest {
    public static void main(String[] args) {
        ArkJurassic.Job job=new ArkJurassic.Job();
        job.bounds=new int[]{448,128,575,255};job.total=16384;
        ArkJurassic.validateJob(job);
        for(int error=0;error<3;error++) {
            job.bounds=new int[]{448,128,575,255};job.total=16384;job.done=0;
            if(error==0) job.done=16385;
            if(error==1) job.bounds[0]=-1025;
            if(error==2) job.total=1;
            boolean rejected=false;
            try{ArkJurassic.validateJob(job);}catch(IllegalStateException expected){rejected=true;}
            if(!rejected) throw new AssertionError("Invalid checkpoint accepted: "+error);
        }
        System.out.println("JobRulesTest: reference dimensions and corrupt/outside checkpoints passed");
    }
}
