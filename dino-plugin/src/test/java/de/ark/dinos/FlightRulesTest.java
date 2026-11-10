package de.ark.dinos;
public final class FlightRulesTest {
 public static void main(String[] args){
  var hover=FlightRules.movement(0,0,false,false,false,false,false,20,60);
  check(hover.x()==0&&hover.y()==0&&hover.z()==0,"release hovers");
  var up=FlightRules.movement(0,0,false,false,false,false,true,20,60); check(up.y()>0,"jump ascends");
  var descent=FlightRules.movement(90,45,true,false,false,false,false,20,60);check(descent.x()<0&&descent.y()<0&&Math.abs(descent.z())<1e-6,"yaw pitch descent");
  check(FlightRules.movement(0,-80,true,false,false,false,true,60,60).y()==0,"altitude cap");
  check(FlightRules.movement(0,80,true,false,false,false,false,60,60).y()<0,"can descend at cap");
  check(FlightRules.movement(0,0,false,false,false,false,true,59.9,60).y()<=.100001,"never overshoot cap");
  var diag=FlightRules.movement(0,0,true,false,true,false,false,20,60);check(Math.abs(Math.hypot(diag.x(),diag.z())-.6)<1e-6,"normal flight covers twelve blocks in twenty ticks, including diagonals");
  check(FlightRules.movement(0,0,true,true,true,true,false,20,60).z()==0,"opposed keys cancel");
  var sprint=FlightRules.movement(0,0,true,false,false,false,false,true,20,60);
  check(Math.abs(sprint.z()*20-18)<1e-6,"sprint flight covers eighteen blocks in twenty ticks");
  var sprintDiagonal=FlightRules.movement(45,0,true,false,true,false,false,true,20,60);
  check(Math.abs(Math.hypot(sprintDiagonal.x(),sprintDiagonal.z())-.9)<1e-6,"sprint diagonals remain bounded");
  var sprintHover=FlightRules.movement(0,0,false,false,false,false,false,true,20,60);
  check(sprintHover.x()==0&&sprintHover.y()==0&&sprintHover.z()==0,"sprint without direction still hovers");
  check(FlightRules.movement(0,0,false,false,false,false,true,true,59.9,60).y()<=.100001,"sprint ascent cannot cross the ceiling");
  for(boolean fast:new boolean[]{false,true})for(float pitch:new float[]{-90,-45,0,45,90}) {
   var motion=FlightRules.movement(90,pitch,true,false,true,false,true,fast,20,60);
   check(Double.isFinite(motion.x())&&Double.isFinite(motion.y())&&Double.isFinite(motion.z()),"finite inputs produce finite velocity");
   check(Math.hypot(motion.x(),motion.z())<=(fast?.9:.6)+1e-9&&motion.y()<=(fast?.4:.3)+1e-9,"all steering stays within horizontal and vertical limits");
  }
  check(FlightRules.movement(Float.NaN,0,true,false,false,false,false,true,20,60).equals(new FlightRules.Motion(0,0,0)),"invalid heading fails stationary");
  check(FlightRules.movement(0,Float.POSITIVE_INFINITY,true,false,false,false,false,true,20,60).equals(new FlightRules.Motion(0,0,0)),"invalid pitch fails stationary");
  check(FlightRules.movement(0,0,true,false,false,false,true,true,20,Double.NaN).equals(new FlightRules.Motion(0,0,0)),"invalid ceiling fails stationary");
  check(FlightRules.autonomousSpeed(2,8)==.32,"unridden cruise moves at 6.4 blocks per second");
  check(FlightRules.autonomousSpeed(2,.04)==.04,"autonomous flight must not overshoot a nearby waypoint");
  check(FlightRules.autonomousSpeed(3,8)==.16,"autonomous landing retains its controlled descent");
  check(FlightRules.autonomousSpeed(2,Double.NaN)==0&&FlightRules.autonomousSpeed(0,8)==0,"invalid autonomous state cannot move");
  System.out.println("PASS: normal/sprint flight, hover, finite steering, ceilings and bounded autonomous waypoints");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
