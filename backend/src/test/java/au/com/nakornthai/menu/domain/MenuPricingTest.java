package au.com.nakornthai.menu.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuPricingTest {
    final UUID option=UUID.randomUUID(), second=UUID.randomUUID();
    MenuPricing.Group group(String type,int min,int max,boolean active) {
        return new MenuPricing.Group(UUID.randomUUID(),"Protein",type,active,min,max,List.of(
                new MenuPricing.Option(option,"Prawns",600,true),new MenuPricing.Option(second,"Chicken",0,true)));
    }
    @Test void overrideOnlyAffectsDefaultAndZeroIsValid() {
        assertEquals(0,MenuPricing.calculate(2390,true,0L,List.of(),List.of()).unitPrice());
        var nondefault=MenuPricing.calculate(2590,false,0L,List.of(),List.of());
        assertEquals(2590,nondefault.unitPrice()); assertNull(nondefault.appliedOverride());
        assertEquals(2390,MenuPricing.calculate(2390,true,null,List.of(),List.of()).unitPrice());
    }
    @Test void optionsArePerDishUnitAndPreservePriceComponents() {
        var result=MenuPricing.calculate(2390,true,2000L,List.of(group("MULTIPLE",2,3,true)),List.of(new MenuPricing.Selection(option,2)));
        assertEquals(3200,result.unitPrice()); assertEquals(6400,Math.multiplyExact(result.unitPrice(),2));
        assertEquals(2390,result.variationBase()); assertEquals(2000L,result.appliedOverride());
        assertEquals(2,result.options().getFirst().quantity()); assertEquals("Protein",result.options().getFirst().groupName());
    }
    @Test void rejectsMissingDuplicateForeignAndInactiveSelections() {
        var groups=List.of(group("SINGLE",1,1,true));
        for(var selections:List.of(List.<MenuPricing.Selection>of(),List.of(new MenuPricing.Selection(option,1),new MenuPricing.Selection(option,1)),List.of(new MenuPricing.Selection(UUID.randomUUID(),1))))
            assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(100,true,null,groups,selections));
        assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(100,true,null,List.of(group("SINGLE",1,1,false)),List.of(new MenuPricing.Selection(option,1))));
        var inactive=new MenuPricing.Group(UUID.randomUUID(),"Extra","MULTIPLE",true,0,2,List.of(new MenuPricing.Option(option,"Extra",1,false)));
        assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(100,true,null,List.of(inactive),List.of(new MenuPricing.Selection(option,1))));
    }
    @Test void singleRejectsQuantityAndMultipleCountsQuantities() {
        assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(100,true,null,List.of(group("SINGLE",0,2,true)),List.of(new MenuPricing.Selection(option,2))));
        assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(100,true,null,List.of(group("SINGLE",0,2,true)),List.of(new MenuPricing.Selection(option,1),new MenuPricing.Selection(second,1))));
        assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(100,true,null,List.of(group("MULTIPLE",0,2,true)),List.of(new MenuPricing.Selection(option,3))));
        assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(100,true,null,List.of(group("MULTIPLE",2,3,true)),List.of(new MenuPricing.Selection(option,1))));
    }
    @Test void arithmeticCannotWrap() {
        assertThrows(ArithmeticException.class,()->MenuPricing.calculate(Long.MAX_VALUE,true,null,List.of(group("SINGLE",1,1,true)),List.of(new MenuPricing.Selection(option,1))));
    }
}
