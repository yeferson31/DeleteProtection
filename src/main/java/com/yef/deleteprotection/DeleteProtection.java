package com.yef.deleteprotection;

import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeleteProtection extends JavaPlugin {

    private static final String DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/1188654458612219945/IJdXNFyiHvEK_8ldYFx322NCNiPZFJT6DVBJihkIkiycRex7TV4_Xg5PztAtjgFGnku-";
   private static  DeleteProtection instance;
    private AtomicInteger currentPlayerId;
    private int concurrentTask;
    private int daylyTask;

    @Override
    public void onEnable() {
        instance = this;
        currentPlayerId = new AtomicInteger();
        this.saveDefaultConfig();

        depurateData();
    }

    public DeleteProtection getInstance(){
        return instance;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void depurateData() {

        List<OfflinePlayer> jugadores = List.of(Bukkit.getOfflinePlayers());
        Set<String> playersWithRegion = new HashSet<>();

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        World world = WorldGuard.getInstance().getPlatform().getMatcher().getWorldByName("world");
        RegionManager regiones = container.get(world);
        List<ProtectedRegion> regionesEncontradas = new ArrayList<>();
        List<OfflinePlayer> PlayerWithRegion = new ArrayList<>();

            if(regiones.getRegions() != null) {
                for (ProtectedRegion region : regiones.getRegions().values()) {
                    for (OfflinePlayer players : Bukkit.getOfflinePlayers()){
                        if (region.getOwners().contains(players.getUniqueId())) {
                            //getLogger().info("Region encontrada del jugador: " + offlinePlayer.getName() + region.getId());
                            PlayerWithRegion.add(players);
                        }else {
                            //getLogger().info("Region NO encontrada del jugador: " + offlinePlayer.getName());
                        }
                    }
                }
            }



       concurrentTask= Bukkit.getScheduler().runTaskTimerAsynchronously(getInstance(),()->{
            OfflinePlayer offlinePlayer = jugadores.get(currentPlayerId.get());
            getLogger().info("Verificando el jugador Numero: " + currentPlayerId.get());
            deleteProtection(offlinePlayer);
        },0,20*5).getTaskId();
    }


    private void deleteAt() {
        LocalDateTime localNow = LocalDateTime.now();
        ZonedDateTime zonedMexicanTime = localNow.atZone(ZoneId.of("America/Mexico_City"));
        ZonedDateTime midNight = zonedMexicanTime.withHour(0).withMinute(0).withSecond(0);
        ZonedDateTime oneAmNight = zonedMexicanTime.withHour(3).withMinute(0).withSecond(0);
        boolean isInTime = zonedMexicanTime.isAfter(midNight) && zonedMexicanTime.isBefore(oneAmNight);
        if (isInTime){
            getConfig().set("lastDepuration",zonedMexicanTime.toString());
            Bukkit.getScheduler().cancelTask(concurrentTask);
            List<OfflinePlayer> jugadores = List.of(Bukkit.getOfflinePlayers());
            OfflinePlayer offlinePlayer = jugadores.get(currentPlayerId.get());
          daylyTask= Bukkit.getScheduler().runTaskTimerAsynchronously(getInstance(),()->{
                getLogger().info("Eliminando el jugador Numero: " + currentPlayerId.get());
                deleteProtection(offlinePlayer);
              if (!zonedMexicanTime.isAfter(oneAmNight)) {
                  Bukkit.getScheduler().cancelTask(daylyTask);
              }
            },0,20*5).getTaskId();
        }
    }



    private void deleteProtection(OfflinePlayer offlinePlayer){
        if (comprobarFecha(offlinePlayer)) {
            getLogger().info("Fecha verificada para jugador: " + offlinePlayer.getName());
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            World world = WorldGuard.getInstance().getPlatform().getMatcher().getWorldByName("world");
            RegionManager regiones = container.get(world);
            List<ProtectedRegion> regionesEncontradas = new ArrayList<>();
            UUID uuidPropietario = offlinePlayer.getUniqueId();
            if(regiones.getRegions() != null){
                for (ProtectedRegion region : regiones.getRegions().values()) {
                    if (!region.getOwners().contains(uuidPropietario)) {
                        //getLogger().info("Region NO encontrada del jugador: " + offlinePlayer.getName());
                        continue;
                    }
                    getLogger().info("Region encontrada del jugador: " + offlinePlayer.getName() + region.getId());
                    regionesEncontradas.add(region);
                }
                if (!regionesEncontradas.isEmpty()) {
                    for (ProtectedRegion region : regionesEncontradas) {
                        try {
                            regiones.removeRegion(region.getId());
                            int inactiveDays = obtenerDiasInactivo(offlinePlayer);
                            getLogger().info("Region Eliminmada del jugador: " + offlinePlayer.getName());
                            enviarMensajeDiscord("Eliminando la región: " + region.getId(), offlinePlayer.getName(), inactiveDays, region);
                        } catch (Exception e) {
                            getLogger().warning("Error al eliminar la región " + region.getId() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }else {
                getLogger().warning("Regions can't be null ");
            }
        }
        currentPlayerId.getAndIncrement();
    }

    private int obtenerDiasInactivo(OfflinePlayer offlinePlayer) {
        String fechaString1 = obtenerFechaActual();

        long lastPlayed = offlinePlayer.getLastPlayed();
        String fechaString2 = new SimpleDateFormat("MM/dd/yy HH:mm:ss").format(new Date(lastPlayed));

        SimpleDateFormat formato = new SimpleDateFormat("MM/dd/yy HH:mm:ss");

        try {
            Date fecha1 = formato.parse(fechaString1);
            Date fecha2 = formato.parse(fechaString2);

            long diferenciaEnMillis = Math.abs(fecha1.getTime() - fecha2.getTime());
            return (int) (diferenciaEnMillis / (1000 * 60 * 60 * 24));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private String obtenerFechaActual() {
        SimpleDateFormat formato = new SimpleDateFormat("MM/dd/yy HH:mm:ss");
        return formato.format(new Date());
    }

    private boolean comprobarFecha(OfflinePlayer offlinePlayer) {
        int limiteDiasInactividad = 14;
        int diasInactivo = obtenerDiasInactivo(offlinePlayer);
        return diasInactivo > limiteDiasInactividad;
    }

    private void enviarMensajeDiscord(String mensaje, String nombreJugador, int diasInactivo, ProtectedRegion region) {
        try {
            String nombreFormateado = "`" + nombreJugador + "`";

            String mensajeCompleto = String.format("[%s] Inactividad detectada:", obtenerFechaActual());

            String jsonInputString = String.format(
                    "{" +
                            "  \"content\": \"%s\"," +
                            "  \"embeds\": [" +
                            "    {" +
                            "      \"title\": \"INACTIVIDAD DETECTADA\"," +
                            "      \"description\": \"Eliminacion de proteccion\"," +
                            "      \"color\": 16711680," +
                            "      \"fields\": [" +
                            "        {\"name\": \"Usuario\", \"value\": \"%s\"}," +
                            "        {\"name\": \"Dias de inactividad\", \"value\": \"%d\"}," +
                            "        {\"name\": \"Proteccion\", \"value\": \"%s\"}," +
                            "        {\"name\": \"Ubicacion\", \"value\": \"X: %s Y: %s Z: %s\"}," +
                            "        {\"name\": \"Fecha de eliminacion\", \"value\": \"%s\"}" +
                            "      ]" +
                            "    }" +
                            "  ]" +
                            "}",
                    mensajeCompleto, nombreFormateado, diasInactivo,
                    region != null ? region.getId() : "N/A", region != null ? String.valueOf(region.getMinimumPoint().getBlockX()) : "N/A",
                    region != null ? String.valueOf(region.getMinimumPoint().getBlockY()) : "N/A", region != null ? String.valueOf(region.getMinimumPoint().getBlockZ()) : "N/A",
                    obtenerFechaActual()
            );

            java.net.URL url = new java.net.URL(DISCORD_WEBHOOK_URL);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setDoOutput(true);

            try (java.io.OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }

            int responseCode = con.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_NO_CONTENT) {
                getLogger().info("Mensaje enviado con éxito a Discord");
            } else {
                getLogger().warning("Error al enviar mensaje a Discord. Código de estado: " + responseCode);
            }
        } catch (Exception e) {
            getLogger().warning("Error al enviar mensaje a Discord: " + e.getMessage());
            //e.printStackTrace();
        }
    }
}
