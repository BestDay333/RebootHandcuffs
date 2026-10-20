package com.reboot.handcuffs;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ежетиковая задача физики наручников: удержание жертвы рядом со "судьёй",
 * притяжение при отдалении и отрисовка частиц-поводка.
 *
 * <p>Оптимизации:
 * <ul>
 *   <li>работают только пары, где оба игрока онлайн и в одном мире;</li>
 *   <li>квадрат расстояния считается без извлечения корня (корень - только при притяжении);</li>
 *   <li>значения конфигурации берутся из кэша, а не из YAML;</li>
 *   <li>повторно используются буферы записей, получателей частиц и один {@link ParticleBuilder};</li>
 *   <li>частицы отправляются только игрокам в радиусе particles.receivers.</li>
 * </ul>
 */
public final class CuffTask extends BukkitRunnable {

    private final RebootHandcuffs plugin;
    private final DataManager dataManager;
    private final PluginSettings settings;

    private final List<CuffRecord> buffer = new ArrayList<>();
    private final List<Player> receiverBuffer = new ArrayList<>();
    private final Set<UUID> liveJudges = new HashSet<>();
    /** Прошлые позиции судей - чтобы считать их скорость и тянуть жертву ровно за ним. */
    private final Map<UUID, Location> lastJudgePositions = new HashMap<>();
    private long tick;

    public CuffTask(RebootHandcuffs plugin, DataManager dataManager, PluginSettings settings) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.settings = settings;
    }

    @Override
    public void run() {
        // Снимок записей: за время итерации что-то могут снять, поэтому состояние перепроверяем.
        buffer.clear();
        buffer.addAll(dataManager.getRecords());
        if (buffer.isEmpty()) {
            lastJudgePositions.clear();
            return;
        }

        tick++;
        ParticleBuilder builder = null;
        if (settings.particleEnabled() && (tick % settings.particleEveryNTicks() == 0L)) {
            builder = settings.particle().builder().count(0);
        }

        liveJudges.clear();

        double maxDistance = settings.maxDistance();
        double maxDistanceSq = maxDistance * maxDistance;
        double maxParticleDistance = settings.particleMaxDistance();
        double maxParticleDistanceSq = maxParticleDistance * maxParticleDistance;

        for (CuffRecord record : buffer) {
            UUID victimId = record.getVictim();
            if (!dataManager.isHandcuffed(victimId)) {
                continue;
            }

            Player victim = Bukkit.getPlayer(victimId);
            if (victim == null || !victim.isOnline()) {
                continue;
            }

            if (!settings.allowVehicles() && victim.isInsideVehicle()) {
                victim.leaveVehicle();
            }

            UUID judgeId = record.getJudge();
            if (judgeId == null) {
                continue; // наручники надеты консольью - притягивать некому
            }

            Player judge = Bukkit.getPlayer(judgeId);
            if (judge == null || !judge.isOnline()) {
                continue; // судья оффлайн - жертва просто стоит на месте
            }

            // Разные миры: distance() бросил бы исключение (так и было в старой версии).
            if (victim.getWorld() != judge.getWorld()) {
                continue;
            }

            liveJudges.add(judgeId);

            Location victimLocation = victim.getLocation();
            Location judgeLocation = judge.getLocation();
            double dx = judgeLocation.getX() - victimLocation.getX();
            double dy = judgeLocation.getY() - victimLocation.getY();
            double dz = judgeLocation.getZ() - victimLocation.getZ();
            double distanceSq = dx * dx + dy * dy + dz * dz;

            double judgeSpeed = updateJudgeSpeed(judgeId, judgeLocation);

            if (distanceSq > maxDistanceSq) {
                pull(victim, dx, dy, dz, distanceSq, maxDistance, judgeSpeed);
            }

            if (builder != null && distanceSq <= maxParticleDistanceSq) {
                drawLeash(builder, victimLocation, dx, dy, dz, distanceSq);
            }
        }

        // Забываем судей, пара с которыми уже не активна.
        lastJudgePositions.keySet().removeIf(id -> !liveJudges.contains(id));
    }

    /** Горизонтальная скорость судьи с прошлого тика (блоков за тик). */
    private double updateJudgeSpeed(UUID judgeId, Location judgeLocation) {
        Location previous = lastJudgePositions.get(judgeId);
        if (previous == null) {
            lastJudgePositions.put(judgeId, judgeLocation.clone());
            return 0.0D;
        }
        double dx = judgeLocation.getX() - previous.getX();
        double dz = judgeLocation.getZ() - previous.getZ();
        previous.setX(judgeLocation.getX());
        previous.setY(judgeLocation.getY());
        previous.setZ(judgeLocation.getZ());
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Плавно подтягивает жертву к судье, если тот ушёл дальше max-distance.
     * Сила притяжения равна скорости судьей (жертва идёт ровно за ним)
     * плюс растяжение поводка за каждый блок превышения.
     */
    private void pull(Player victim, double dx, double dy, double dz,
                      double distanceSq, double maxDistance, double judgeSpeed) {
        double distance = Math.sqrt(distanceSq);
        double invDistance = 1.0D / distance;
        double strength = Math.min(
                settings.maxPullStrength(),
                judgeSpeed * 1.2D + (distance - maxDistance) * settings.pullStrength());
        Vector velocity = new Vector(dx * invDistance * strength, 0.0D, dz * invDistance * strength);
        if (dy > 0.5D) {
            // Судья выше: подтягиваем вверх с импульсом прыжка, чтобы перешагивать блоки.
            velocity.setY(Math.max(dy * settings.heightFollow(), 0.42D));
        } else if (dy < -0.5D) {
            velocity.setY(dy * settings.heightFollow());
        }
        victim.setVelocity(velocity);
        victim.setFallDistance(0f);
    }

    /** Рисует линию из частиц от жертвы к судье (в начале координат - грудь жертвы). */
    private void drawLeash(ParticleBuilder builder, Location from, double dx, double dy, double dz, double distanceSq) {
        World world = from.getWorld();
        if (world == null) {
            return;
        }

        // Получатели считаются от позиции жертвы (в радиусе particles.receivers).
        receiverBuffer.clear();
        double receiverDistanceSq = settings.particleReceivers() * (double) settings.particleReceivers();
        for (Player player : world.getPlayers()) {
            Location playerLocation = player.getLocation();
            double rx = playerLocation.getX() - from.getX();
            double ry = playerLocation.getY() - from.getY();
            double rz = playerLocation.getZ() - from.getZ();
            if (rx * rx + ry * ry + rz * rz <= receiverDistanceSq) {
                receiverBuffer.add(player);
            }
        }
        if (receiverBuffer.isEmpty()) {
            return;
        }

        int points = (int) Math.ceil(Math.sqrt(distanceSq) * settings.particleDensity());
        if (points > settings.particleMaxPoints()) {
            points = settings.particleMaxPoints();
        }
        if (points < 1) {
            return;
        }

        double stepX = dx / points;
        double stepY = dy / points;
        double stepZ = dz / points;
        double startY = from.getY() + 0.5D;

        // Один переиспользуемый Location на всю линию - без аллокаций в цикле.
        Location point = new Location(world, from.getX(), startY, from.getZ());

        // Важно: location задаётся ДО receivers(...). Часть перегрузок receivers(...)
        // в Paper 26.3 бросает IllegalStateException("Please set location first"),
        // если позиция частиц ещё не установлена. location(Location) внутри клонирует
        // объект, поэтому последующие point.set(...) байлдера не ломают.
        builder.location(point);
        builder.receivers(receiverBuffer);

        for (int i = 1; i <= points; i++) {
            builder.location(point.set(
                    from.getX() + stepX * i,
                    startY + stepY * i,
                    from.getZ() + stepZ * i)).spawn();
        }
    }
}
