package treechopper.proxy;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import treechopper.common.compat.IC2Compat;
import treechopper.common.config.ConfigurationHandler;
import treechopper.common.handler.TreeHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class PlayerInteract {

  public BlockPos m_BlockPos; // Interact block position
  public float m_LogCount;
  public double m_ToolState; // remaining vanilla durability, or IC2 charge for electric tools

  public PlayerInteract(BlockPos blockPos, float logCount, double toolState) {
    m_BlockPos = blockPos;
    m_LogCount = logCount;
    m_ToolState = toolState;
  }
};

public class CommonProxy {

  public static Map<UUID, Boolean> m_PlayerPrintNames = new HashMap<>();
  protected static Map<UUID, PlayerInteract> m_PlayerData = new HashMap<>();
  protected TreeHandler treeHandler;

  @SubscribeEvent
  public void InteractWithTree(PlayerInteractEvent interactEvent) {

    UUID playerId = interactEvent.getEntityPlayer().getPersistentID();

    if (interactEvent.getSide().isClient() && m_PlayerPrintNames.containsKey(playerId) && m_PlayerPrintNames.get(playerId)) {
      interactEvent.getEntityPlayer().sendMessage(new TextComponentTranslation(I18n.format("proxy.printBlock") + " " + interactEvent.getWorld().getBlockState(interactEvent.getPos()).getBlock().getUnlocalizedName()));
      interactEvent.getEntityPlayer().sendMessage(new TextComponentTranslation(I18n.format("proxy.printMainHand") + " " + interactEvent.getEntityPlayer().getHeldItemMainhand().getUnlocalizedName()));
    }

    int logCount;
    boolean shifting = true;

    if (!ConfigurationHandler.disableShift) {
      if (interactEvent.getEntityPlayer().isSneaking() && !ConfigurationHandler.reverseShift) {
        shifting = false;
      }

      if (!interactEvent.getEntityPlayer().isSneaking() && ConfigurationHandler.reverseShift) {
        shifting = false;
      }
    }

    if (CheckWoodenBlock(interactEvent.getWorld(), interactEvent.getPos()) && CheckItemInHand(interactEvent.getEntityPlayer()) && shifting) {

      ItemStack heldItem = interactEvent.getEntityPlayer().getHeldItemMainhand();
      boolean isElectric = IC2Compat.isElectricItem(heldItem);
      int axeDurability = heldItem.getMaxDamage() - heldItem.getItemDamage();
      double toolState = isElectric ? IC2Compat.getCharge(heldItem) : axeDurability;

      if (m_PlayerData.containsKey(playerId) &&
              m_PlayerData.get(playerId).m_BlockPos.equals(interactEvent.getPos()) &&
              m_PlayerData.get(playerId).m_ToolState == toolState) {
        return;
      }

      treeHandler = new TreeHandler();
      logCount = treeHandler.AnalyzeTree(interactEvent.getWorld(), interactEvent.getPos(), interactEvent.getEntityPlayer());

      boolean notEnoughDurability = !isElectric && heldItem.isItemStackDamageable() && axeDurability < logCount * ConfigurationHandler.durabilityLossFactor;
      boolean notEnoughEnergy = isElectric && ConfigurationHandler.ic2EnergyPerLog != 0 && !IC2Compat.canUse(heldItem, logCount * ConfigurationHandler.ic2EnergyPerLog);

      if (notEnoughDurability || notEnoughEnergy) {
        m_PlayerData.remove(playerId);

        if (!interactEvent.getSide().isClient()) {
          if (notEnoughEnergy) {
            double missingEnergy = Math.ceil(logCount * ConfigurationHandler.ic2EnergyPerLog - IC2Compat.getCharge(heldItem));
            interactEvent.getEntityPlayer().sendMessage(new TextComponentTranslation("proxy.notEnoughEnergy", (int) missingEnergy));
          } else {
            int missingDurability = (int) Math.ceil(logCount * ConfigurationHandler.durabilityLossFactor - axeDurability);
            interactEvent.getEntityPlayer().sendMessage(new TextComponentTranslation("proxy.notEnoughDurability", missingDurability));
          }
        }

        return;
      }

      if (logCount > 1) {
        m_PlayerData.put(playerId, new PlayerInteract(interactEvent.getPos(), logCount, toolState));
      }
    } else {
      m_PlayerData.remove(playerId);
    }
  }

  @SubscribeEvent
  public void BreakingBlock(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed breakSpeed) {

    UUID playerId = breakSpeed.getEntityPlayer().getPersistentID();

    if (m_PlayerData.containsKey(playerId)) {

      BlockPos blockPos = m_PlayerData.get(playerId).m_BlockPos;

      if (blockPos.equals(breakSpeed.getPos())) {
        breakSpeed.setNewSpeed(breakSpeed.getOriginalSpeed() / (m_PlayerData.get(playerId).m_LogCount / 2.0f));
      } else {
        breakSpeed.setNewSpeed(breakSpeed.getOriginalSpeed());
      }
    }
  }

  @SubscribeEvent
  public void DestroyWoodBlock(BlockEvent.BreakEvent breakEvent) {

    UUID playerId = breakEvent.getPlayer().getPersistentID();

    if (m_PlayerData.containsKey(playerId)) {

      BlockPos blockPos = m_PlayerData.get(playerId).m_BlockPos;

      if (blockPos.equals(breakEvent.getPos())) {
        float logCount = m_PlayerData.get(playerId).m_LogCount;

        treeHandler.DestroyTree(breakEvent.getWorld(), breakEvent.getPlayer());

        if (!breakEvent.getPlayer().isCreative()) {

          ItemStack heldItem = breakEvent.getPlayer().getHeldItemMainhand();

          if (IC2Compat.isElectricItem(heldItem)) {
            if (ConfigurationHandler.ic2EnergyPerLog != 0) {
              IC2Compat.dischargeEnergy(heldItem, logCount * ConfigurationHandler.ic2EnergyPerLog, breakEvent.getPlayer());
            }
          } else if (heldItem.isItemStackDamageable() && ConfigurationHandler.durabilityLossFactor != 0) {
            int damageAmount = (int) (logCount * ConfigurationHandler.durabilityLossFactor);

            heldItem.damageItem(damageAmount, breakEvent.getPlayer());
          }
        }
      }
    }
  }

  protected boolean CheckWoodenBlock(World world, BlockPos blockPos) {

    if (ConfigurationHandler.blockWhiteList.contains(world.getBlockState(blockPos).getBlock().getUnlocalizedName())) {
      return true;
    }

    if (!world.getBlockState(blockPos).getBlock().isWood(world, blockPos)) {
      return false;
    }

    return true;
  }

  protected boolean CheckItemInHand(EntityPlayer entityPlayer) {

    if (entityPlayer.getHeldItemMainhand().isEmpty()) {
      return false;
    }

    Item itemHand = entityPlayer.getHeldItemMainhand().getItem();

    if (ConfigurationHandler.axeTypes.contains(itemHand.getUnlocalizedName())) {
      return true;
    }

    if (itemHand instanceof ItemAxe) {
      return true;
    }

    return false;
  }
}