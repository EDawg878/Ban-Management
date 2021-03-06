package me.confuser.banmanager.storage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;

import me.confuser.banmanager.BanManager;
import me.confuser.banmanager.data.PlayerBanData;
import me.confuser.banmanager.data.PlayerData;
import me.confuser.banmanager.events.PlayerBanEvent;
import me.confuser.banmanager.events.PlayerUnbanEvent;
import me.confuser.banmanager.util.DateUtils;

public class PlayerBanStorage extends BaseDaoImpl<PlayerBanData, Integer> {

      private BanManager plugin = BanManager.getPlugin();
      private ConcurrentHashMap<UUID, PlayerBanData> bans = new ConcurrentHashMap<>();

      public PlayerBanStorage(ConnectionSource connection, DatabaseTableConfig<PlayerBanData> tableConfig) throws SQLException {
            super(connection, tableConfig);

            if (!this.isTableExists()) {
                  return;
            }

            // TODO Use raw query, to avoid N+2 queries
		/* String playerTable = plugin.getPlayerStorage().getTableInfo().getTableName();
             GenericRawResults<String[]> rawResults = this.queryRaw("SELECT * FROM " + getTableInfo().getTableName() + " b JOIN " + playerTable + " p ON b.player_id = p.id JOIN " + playerTable + " a ON b.actor_id = a.id", null);
             */
            CloseableIterator<PlayerBanData> itr = iterator();

            while (itr.hasNext()) {
                  PlayerBanData ban = itr.next();

                  bans.put(ban.getPlayer().getUUID(), ban);
            }

            itr.close();

            plugin.getLogger().info("Loaded " + bans.size() + " bans into memory");
      }

      public ConcurrentHashMap<UUID, PlayerBanData> getBans() {
            return bans;
      }

      public boolean isBanned(UUID uuid) {
            return bans.get(uuid) != null;
      }

      public boolean isBanned(String playerName) {
            return getBan(playerName) != null;
      }

      public PlayerBanData getBan(UUID uuid) {
            return bans.get(uuid);
      }

      public void addBan(PlayerBanData ban) {
            bans.put(ban.getPlayer().getUUID(), ban);
      }

      public void removeBan(PlayerBanData ban) {
            removeBan(ban.getPlayer().getUUID());
      }

      public void removeBan(UUID uuid) {
            bans.remove(uuid);
      }

      public PlayerBanData getBan(String playerName) {
            for (PlayerBanData ban : bans.values()) {
                  if (ban.getPlayer().getName().equalsIgnoreCase(playerName)) {
                        return ban;
                  }
            }

            return null;
      }

      public boolean ban(PlayerBanData ban) throws SQLException {
            PlayerBanEvent event = new PlayerBanEvent(ban);
            Bukkit.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                  return false;
            }

            create(ban);
            bans.put(ban.getPlayer().getUUID(), ban);

            return true;
      }

      public boolean unban(PlayerBanData ban, PlayerData actor) throws SQLException {
            PlayerUnbanEvent event = new PlayerUnbanEvent(ban);
            Bukkit.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                  return false;
            }

            delete(ban);
            bans.remove(ban.getPlayer().getUUID());

            plugin.getPlayerBanRecordStorage().addRecord(ban, actor);

            return true;
      }

      public CloseableIterator<PlayerBanData> findBans(long fromTime) throws SQLException {
            if (fromTime == 0) {
                  return iterator();
            }

            long checkTime = fromTime + DateUtils.getTimeDiff();

            QueryBuilder<PlayerBanData, Integer> query = queryBuilder();
            Where<PlayerBanData, Integer> where = query.where();
            where
                    .ge("created", checkTime)
                    .or()
                    .ge("updated", checkTime);

            query.setWhere(where);

            return query.iterator();

      }

      public List<PlayerData> getDuplicates(long ip) {
            ArrayList<PlayerData> players = new ArrayList<PlayerData>();

            QueryBuilder<PlayerBanData, Integer> query = queryBuilder();
            try {
                  QueryBuilder<PlayerData, byte[]> playerQuery = plugin.getPlayerStorage().queryBuilder();

                  Where<PlayerData, byte[]> where = playerQuery.where();
                  where.eq("ip", ip);
                  playerQuery.setWhere(where);

                  query.leftJoin(playerQuery);

                  CloseableIterator<PlayerBanData> itr = query.iterator();

                  while (itr.hasNext()) {
                        players.add(itr.next().getPlayer());
                  }

                  itr.close();
            } catch (SQLException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
            }

            return players;
      }
}
