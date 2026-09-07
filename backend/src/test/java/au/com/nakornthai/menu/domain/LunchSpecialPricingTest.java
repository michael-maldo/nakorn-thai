package au.com.nakornthai.menu.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LunchSpecialPricingTest {
    UUID id(String name) { return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    MenuPricing.Option option(String name,long delta) { return new MenuPricing.Option(id(name),name,delta,true); }
    MenuPricing.Group group(boolean friedRice) {
        var options=friedRice?List.of(option("Chicken",0),option("Beef",0)):
                List.of(option("Beef",0),option("Chicken",0),option("Veg & Tofu",0),option("Prawns",600),option("Seafood",800),option("Crispy Pork",500),option("Fish",600));
        return new MenuPricing.Group(id("Protein"),"Protein","SINGLE",true,1,1,options);
    }
    MenuPricing.Price price(boolean rice,MenuPricing.Selection... selections) {
        return MenuPricing.calculate(1490,true,null,List.of(group(rice)),List.of(selections));
    }
    MenuPricing.Selection choose(String name,int qty) { return new MenuPricing.Selection(id(name),qty); }
    @Test void printedDeltasAreAddedPerUnit() {
        for(var entry:Map.of("Chicken",1490,"Beef",1490,"Veg & Tofu",1490,"Prawns",2090,"Seafood",2290,"Crispy Pork",1990,"Fish",2090).entrySet())
            assertEquals(entry.getValue().longValue(),price(false,choose(entry.getKey(),1)).unitPrice());
        var prawns=price(false,choose("Prawns",1)); assertEquals(4180,2*prawns.unitPrice());
        assertEquals(1490,prawns.variationBase());assertNull(prawns.appliedOverride());assertEquals(600,prawns.options().getFirst().delta());
    }
    @Test void requiredSingleRejectsMissingMultipleQuantitiesAndUnrelatedOptions() {
        assertThrows(IllegalArgumentException.class,()->price(false));
        assertThrows(IllegalArgumentException.class,()->price(false,choose("Chicken",2)));
        assertThrows(IllegalArgumentException.class,()->price(false,choose("Chicken",1),choose("Beef",1)));
        assertThrows(IllegalArgumentException.class,()->price(false,choose("Unrelated",1)));
        assertEquals(1490,price(false,choose("Chicken",1)).unitPrice());
    }
    @Test void l4OnlyAllowsChickenOrBeef() {
        assertEquals(1490,price(true,choose("Chicken",1)).unitPrice());
        assertEquals(1490,price(true,choose("Beef",1)).unitPrice());
        assertThrows(IllegalArgumentException.class,()->price(true,choose("Prawns",1)));
        assertThrows(IllegalArgumentException.class,()->price(true,choose("Veg & Tofu",1)));
    }
}
