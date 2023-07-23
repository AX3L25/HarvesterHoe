package fr.axel.dev;

// Importation des bibliothèques nécessaires
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

// Déclaration de la classe principale qui étend JavaPlugin et implémente Listener
public class HarvesterHoe extends JavaPlugin implements Listener {

    // Déclaration de l'économie comme variable privée statique
    private static Economy econ = null;

    // Méthode appelée lors de l'activation du plugin
    @Override
    public void onEnable() {
        // Vérification de la dépendance Vault
        if (!setupEconomy() ) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Enregistrement des événements et des commandes
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("giveharvesterhoe").setExecutor(this);
    }

    // Méthode pour configurer l'économie
    private boolean setupEconomy() {
        // Vérification de la présence du plugin Vault
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        // Obtention du fournisseur de services économiques
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        // Attribution du fournisseur à la variable econ
        econ = rsp.getProvider();
        return econ != null;
    }

    // Méthode appelée lors de l'exécution d'une commande
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérification de la commande
        if (command.getName().equalsIgnoreCase("giveharvesterhoe")) {
            // Vérification du type de l'expéditeur
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Cette commande ne peut être exécutée que par un joueur !");
                return true;
            }

            // Création de la houe et ajout à l'inventaire du joueur
            ItemStack harvesterHoe = createHarvesterHoe();
            player.getInventory().addItem(harvesterHoe);
            player.sendMessage(ChatColor.GREEN + "Vous avez reçu une Harvester Hoe !");
            return true;
        }
        return super.onCommand(sender, command, label, args);
    }

    // Méthode appelée lors de la destruction d'un bloc
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Récupération du joueur et de l'item en main
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        // Vérification de l'item en main
        if (handItem.getType() == Material.WOODEN_HOE && isHarvesterHoe(handItem)) {

            // Comptage du blé dans l'inventaire
            int wheatCount = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.WHEAT) {
                    wheatCount += item.getAmount();
                    item.setAmount(0); // Retire le blé de l'inventaire du joueur en fonction du blé vendu
                }
            }

            // Ajout du système de vente automatique avec Vault
            double price = wheatCount * 0.5; // Prix de vente du blé
            econ.depositPlayer(player, price);
            player.sendMessage(ChatColor.GREEN + "Vous avez vendu " + wheatCount + " blé pour " + price + "€ !");

            // Gestion de l'XP et du niveau de la houe
            int currentLevel = getHoeLevel(handItem);
            int xpRequired = 150 * (currentLevel + 1); // Changement du nombre de blocs requis pour passer au niveau suivant
            int newXp = getXp(handItem) + wheatCount;

            // Vérification de l'XP pour le passage au niveau supérieur
            if (newXp >= xpRequired) {
                setHoeLevel(handItem, currentLevel + 1);
                setXp(handItem, newXp - xpRequired);
                player.sendMessage(ChatColor.GREEN + "Votre Harvester Hoe a été améliorée au niveau " + (currentLevel + 1) + " !");

                // Changement du matériau de la houe en fonction du niveau
                if (currentLevel == 1) {
                    handItem.setType(Material.STONE_HOE);
                } else if (currentLevel == 2) {
                    handItem.setType(Material.IRON_HOE);
                } else if (currentLevel == 3) {
                    handItem.setType(Material.DIAMOND_HOE);
                } else if (currentLevel >= 4) {
                    handItem.setType(Material.NETHERITE_HOE);
                }
            } else {
                setXp(handItem, newXp);
            }

            // Gestion du drop de blé
            Block block = event.getBlock();            
            player.getInventory().addItem(new ItemStack(Material.WHEAT_SEEDS));
            player.sendMessage(ChatColor.GREEN + "Vous avez récolté du blé et replanté les graines !");
        }
    }

    // Méthode pour créer la houe
    private ItemStack createHarvesterHoe() {
        ItemStack harvesterHoe = new ItemStack(Material.WOODEN_HOE); // La houe commence en bois
        ItemMeta meta = harvesterHoe.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + String.valueOf(ChatColor.BOLD) + "Harvester Hoe");

        // Ajout de l'effet d'enchantement
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        // Faire en sorte que l'effet d'enchantement ne s'affiche pas dans le lore
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Ajout du lore
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Level: 0");
        lore.add(ChatColor.GRAY + "XP: 0 / 150"); // Changement du nombre de blocs requis pour passer au niveau suivant
        meta.setLore(lore);

        harvesterHoe.setItemMeta(meta);
        return harvesterHoe;
    }

    // Méthode pour vérifier si l'item est une houe
    private boolean isHarvesterHoe(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore() && meta.getLore().get(0).startsWith(ChatColor.GRAY + "Level:"); // La houe peut être de n'importe quel niveau
    }

    // Méthode pour obtenir le niveau de la houe
    private int getHoeLevel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            String levelLine = meta.getLore().get(0);
            try {
                return Integer.parseInt(levelLine.substring(levelLine.lastIndexOf(" ") + 1));
            } catch (NumberFormatException e) {
                // Ignore invalid lore format
            }
        }
        return 0;
    }

    // Méthode pour obtenir l'XP de la houe
    private int getXp(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            String xpLine = meta.getLore().get(1);
            try {
                return Integer.parseInt(xpLine.substring(xpLine.indexOf(":") + 2, xpLine.indexOf("/")));
            } catch (NumberFormatException e) {
                // Ignore invalid lore format
            }
        }
        return 0;
    }

    // Méthode pour définir le niveau de la houe
    private void setHoeLevel(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            lore.set(0, ChatColor.GRAY + "Level: " + level);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    // Méthode pour définir l'XP de la houe
    private void setXp(ItemStack item, int xp) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            String xpLine = lore.get(1);
            int slashIndex = xpLine.indexOf("/");
            lore.set(1, xpLine.substring(0, xpLine.indexOf(":") + 2) + " " + xp + xpLine.substring(slashIndex));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
}

