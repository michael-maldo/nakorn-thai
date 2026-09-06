package au.com.nakornthai.menu.domain;

import java.util.*;

/** Authoritative configuration validation and checked minor-unit arithmetic. */
public final class MenuPricing {
    private MenuPricing() {}
    public record Selection(UUID optionId, int quantity) {}
    public record Option(UUID id, String name, long delta, boolean active) {}
    public record Group(UUID id, String name, String type, boolean active, int min, int max, List<Option> options) {}
    public record Chosen(UUID optionId, String groupName, String optionName, long delta, int quantity) {}
    public record Price(long variationBase, Long appliedOverride, long unitPrice, List<Chosen> options) {}

    public static Price calculate(long variationBase, boolean defaultVariation, Long override,
                                  List<Group> groups, List<Selection> selections) {
        var remaining = new HashMap<UUID, Integer>();
        for (var selection : selections) {
            if (selection.optionId() == null || selection.quantity() < 1 || selection.quantity() > 20
                    || remaining.putIfAbsent(selection.optionId(), selection.quantity()) != null)
                throw new IllegalArgumentException("Invalid or duplicate option selection");
        }
        Long applied = defaultVariation ? override : null;
        long total = applied == null ? variationBase : applied;
        if (variationBase < 0 || total < 0) throw new IllegalArgumentException("Invalid base price");
        var chosen = new ArrayList<Chosen>();
        for (var group : groups) {
            int count = 0;
            for (var option : group.options()) {
                var quantity = remaining.remove(option.id());
                if (quantity == null) continue;
                if (!group.active() || !option.active() || option.delta() < 0)
                    throw new IllegalArgumentException("Selected option is unavailable");
                if ("SINGLE".equals(group.type()) && quantity != 1)
                    throw new IllegalArgumentException("Single-choice option quantity must be one");
                count = Math.addExact(count, quantity);
                total = Math.addExact(total, Math.multiplyExact(option.delta(), quantity));
                chosen.add(new Chosen(option.id(), group.name(), option.name(), option.delta(), quantity));
            }
            if (count < group.min() || count > group.max() || ("SINGLE".equals(group.type()) && count > 1))
                throw new IllegalArgumentException("Option selection requirements are not satisfied: " + group.name());
        }
        if (!remaining.isEmpty()) throw new IllegalArgumentException("Option does not belong to this dish");
        chosen.sort(Comparator.comparing(Chosen::optionId));
        return new Price(variationBase, applied, total, List.copyOf(chosen));
    }
}
