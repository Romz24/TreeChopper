package treechopper.common.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class IC2Compat {

  private static boolean available;
  private static Object manager;
  private static Method useMethod;
  private static Method canUseMethod;
  private static Method getChargeMethod;
  private static Class<?> electricItemInterface;

  static {
    try {
      Class<?> electricItemClass = Class.forName("ic2.api.item.ElectricItem");
      Field managerField = electricItemClass.getField("manager");
      manager = managerField.get(null);
      useMethod = manager.getClass().getMethod("use", ItemStack.class, double.class, EntityLivingBase.class);
      canUseMethod = manager.getClass().getMethod("canUse", ItemStack.class, double.class);
      getChargeMethod = manager.getClass().getMethod("getCharge", ItemStack.class);
      electricItemInterface = Class.forName("ic2.api.item.IElectricItem");
      available = true;
    } catch (Throwable t) {
      available = false;
    }
  }

  // True only for items IC2 itself recognizes as electric (i.e. items with a real energy store, not vanilla durability).
  public static boolean isElectricItem(ItemStack stack) {
    return available && !stack.isEmpty() && electricItemInterface.isInstance(stack.getItem());
  }

  // Whether the item currently holds at least `amount` energy. No-op-safe for non-electric items.
  public static boolean canUse(ItemStack stack, double amount) {
    if (!available) {
      return false;
    }

    try {
      return (boolean) canUseMethod.invoke(manager, stack, amount);
    } catch (Exception e) {
      return false;
    }
  }

  // Currently stored energy. Returns 0 for non-electric items or when IC2 isn't loaded.
  public static double getCharge(ItemStack stack) {
    if (!available) {
      return 0;
    }

    try {
      return (double) getChargeMethod.invoke(manager, stack);
    } catch (Exception e) {
      return 0;
    }
  }

  // Drains IC2 energy from an electric item (no-op for non-electric items or when IC2 isn't loaded).
  public static void dischargeEnergy(ItemStack stack, double amount, EntityLivingBase entity) {
    if (!available || amount <= 0) {
      return;
    }

    try {
      useMethod.invoke(manager, stack, amount, entity);
    } catch (Exception ignored) {
    }
  }
}
