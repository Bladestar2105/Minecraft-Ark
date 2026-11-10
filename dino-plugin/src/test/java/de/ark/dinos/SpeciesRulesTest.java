package de.ark.dinos;
import java.util.Set;
public final class SpeciesRulesTest {
 public static void main(String[] args) {
  String[] names={"triceratops","stegosaurus","trex","brachiosaurus","pteranodon","velociraptor"}; int[] counts=new int[6];
  for(int n=1;n<=12;n++) for(int d=0;d<2;d++) { int index=(n-1)%6; boolean ravager=index==2||index==5;
   var result=SpeciesRules.resolve(null,Set.of("ark_renewal_habitat_%02d_dino_%d".formatted(n,d)),false,ravager);
   check(names[index].equals(result.species()),"habitat "+n+"/"+d); counts[index]++;
   check(names[index].equals(SpeciesRules.resolve(result.species(),Set.of(),true,ravager).species()),"repeat restore");
  }
  for(int count:counts) check(count==4,"four of each species");
  check(SpeciesRules.resolve(null,Set.of(),false,false).species()==null,"ordinary sniffer untouched");
  check(SpeciesRules.resolve(null,Set.of(),true,false).species().equals("triceratops"),"legacy owned sniffer");
  check(SpeciesRules.resolve(null,Set.of(),true,true).species().equals("trex"),"legacy owned ravager");
  check(SpeciesRules.resolve("pteranodon",Set.of("ark_renewal_habitat_01_dino_0"),true,false).species().equals("pteranodon"),"PDC wins");
  check(SpeciesRules.resolve("pteranodon",Set.of("ark_dino"),true,true).species().equals("trex"),"incompatible PDC rejected");
  check(SpeciesRules.resolve("dilophosaurus",Set.of("ark_dino"),false,true).species().equals("dilophosaurus"),"Dilo persists on Ravager carrier");
  check(!SpeciesRules.resolve("dilophosaurus",Set.of("ark_dino"),true,false).species().equals("dilophosaurus"),"Dilo rejects incompatible Sniffer carrier");
  for(var tags: java.util.List.of(Set.of("ark_renewal_habitat_02_dino_0","ark_renewal_habitat_05_dino_0"),Set.of("ark_renewal_habitat_05_dino_0","ark_renewal_habitat_02_dino_0"))) {
   var result=SpeciesRules.resolve(null,tags,false,false); check(result.conflict()&&result.species().equals("triceratops"),"conflict defaults consistently");
  }
  check(SpeciesRules.resolve(null,Set.of("ark_renewal_habitat_13_dino_0"),false,false).species()==null,"exact legacy tags only");
  var wildMosa=Set.of("ark_dino","ark_dino_wild");
  check(SpeciesRules.resolve("mosasaurus",wildMosa,false,false,true).species().equals("mosasaurus"),"explicit wild Dolphin Mosa persists");
  check(SpeciesRules.resolve("mosasaurus",wildMosa,true,false,true).species()==null,"Mosa cannot restore as owned");
  check(SpeciesRules.resolve("mosasaurus",Set.of("ark_dino"),false,false,true).species()==null,"Mosa requires explicit wild marker");
  check(SpeciesRules.resolve(null,Set.of(),false,false,true).species()==null,"ordinary Dolphin untouched");
  check(SpeciesRules.resolve(null,Set.of("ark_renewal_habitat_03_dino_0"),false,false,true).species()==null,"Dolphin never inherits legacy land habitat");
  check(!SpeciesRules.resolve("mosasaurus",wildMosa,false,true,false).species().equals("mosasaurus"),"Mosa rejects Ravager");
  check(!SpeciesRules.resolve("mosasaurus",wildMosa,false,false,false).species().equals("mosasaurus"),"Mosa rejects Sniffer");
  check(SpeciesRules.aquatic("mosasaurus")&&!SpeciesRules.aquatic("trex"),"aquatic classifier remains exact");
  System.out.println("PASS: species mapping, compatibility, persistence and conflict checks");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
