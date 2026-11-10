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
  var diag=FlightRules.movement(0,0,true,false,true,false,false,20,60);check(Math.abs(Math.hypot(diag.x(),diag.z())-.35)<1e-6,"diagonal normalized");
  check(FlightRules.movement(0,0,true,true,true,true,false,20,60).z()==0,"opposed keys cancel");
  System.out.println("PASS: flight hover, ascent, pitch descent, cap and normalized input");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
