package com.chai.miniGolf;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.chai.miniGolf.managers.GolfingCourseManager.GolfingInfo;
import com.chai.miniGolf.models.Teleporters;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import static com.chai.miniGolf.Main.getPlugin;

public class ProjectileListener implements Listener
{
	private final Main plugin = JavaPlugin.getPlugin(Main.class);

	@EventHandler
	public void onProjectileHit(ProjectileHitEvent event) {
		Entity ent = event.getEntity();
		if (ent instanceof Snowball) {
			// Check if golf ball
			PersistentDataContainer c = ent.getPersistentDataContainer();
			Optional<GolfingInfo> golfingInfo = getPlugin().golfingCourseManager().getGolfingInfoFromGolfball((Snowball) ent);
			Optional<UUID> golferUuid = getPlugin().golfingCourseManager().getPUuidFromGolfball((Snowball) ent);
			if (!c.has(plugin.strokesKey, PersistentDataType.INTEGER) || golfingInfo.isEmpty()) {
				return;
			}

			// Golf ball hit entity
			if (event.getHitBlockFace() == null) {
				event.setCancelled(true);
				return;
			}

			// Get info
			Location loc = ent.getLocation();
			Vector vel = ent.getVelocity();
			World world = ent.getWorld();

			// Spawn new golf ball
			Snowball ball = (Snowball) world.spawnEntity(loc, EntityType.SNOWBALL);
			ball.setGravity(ent.hasGravity());
			golfingInfo.get().setGolfball(ball);

			// Par
			int par = c.get(plugin.strokesKey, PersistentDataType.INTEGER);
			String owner = c.get(plugin.ownerNameKey, PersistentDataType.STRING);
			PersistentDataContainer b = ball.getPersistentDataContainer();
			b.set(plugin.strokesKey, PersistentDataType.INTEGER, par);
			b.set(plugin.ownerNameKey, PersistentDataType.STRING, owner);
			ball.setCustomName(owner + " - " + par);
			ball.setCustomNameVisible(true);

			// Last pos
			double x = c.get(plugin.xKey, PersistentDataType.DOUBLE);
			double y = c.get(plugin.yKey, PersistentDataType.DOUBLE);
			double z = c.get(plugin.zKey, PersistentDataType.DOUBLE);
			b.set(plugin.xKey, PersistentDataType.DOUBLE, x);
			b.set(plugin.yKey, PersistentDataType.DOUBLE, y);
			b.set(plugin.zKey, PersistentDataType.DOUBLE, z);

			// Was in Bubble Column
			int bubbleColumnStatus = c.get(plugin.bubbleColumnKey, PersistentDataType.INTEGER);
			b.set(plugin.bubbleColumnKey, PersistentDataType.INTEGER, bubbleColumnStatus);

			// Bounce off surfaces
			Material mat = event.getHitBlock().getType();
			switch (event.getHitBlockFace()) {
				case NORTH:
				case SOUTH:
					if (mat == Material.HONEY_BLOCK) {
						vel.setZ(0);
						//loc.setZ(Math.round(loc.getZ()));
						//ball.teleport(loc);
					} else if (mat == Material.SLIME_BLOCK) {
						vel.setZ(1.25 * -vel.getZ());
					} else if (mat == Material.IRON_BARS && getPlugin().config().getFlagPoleStopsVelocity()) {
						handleIronBars(vel, ball);
					} else if (mat == Material.OBSIDIAN && golferUuid.isPresent()) {
						Vector newVel = handleTeleporter(golferUuid.get(), golfingInfo.get(), event.getHitBlock(), event.getHitBlockFace(), vel, ball);
						if (newVel == null) { // Obsidian wasn't a teleporter
							vel.setZ(-vel.getZ());
						} else {
							vel = newVel;
						}
					} else {
						vel.setZ(-vel.getZ());
					}
					break;

				case EAST:
				case WEST:
					if (mat == Material.HONEY_BLOCK) {
						vel.setX(0);
						//loc.setX(Math.round(loc.getX()));
						//ball.teleport(loc);
					} else if (mat == Material.SLIME_BLOCK) {
						vel.setX(1.25 * -vel.getX());
					} else if (mat == Material.IRON_BARS && getPlugin().config().getFlagPoleStopsVelocity()) {
						handleIronBars(vel, ball);
					} else if (mat == Material.OBSIDIAN && golferUuid.isPresent()) {
						Vector newVel = handleTeleporter(golferUuid.get(), golfingInfo.get(), event.getHitBlock(), event.getHitBlockFace(), vel, ball);
						if (newVel == null) { // Obsidian wasn't a teleporter
							vel.setX(-vel.getX());
						} else {
							vel = newVel;
						}
					} else {
						vel.setX(-vel.getX());
					}
					break;

				case UP:
				case DOWN:
					if (event.getHitBlock().getType() == Material.SOUL_SAND || (loc.getBlock().getType() == Material.WATER && loc.getBlock().getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0 && !(loc.getBlock().getBlockData() instanceof BubbleColumn))) {
						// Move ball to last location
						ball.setVelocity(new Vector(0, 0, 0));
						ball.teleport(new Location(world, x, y, z));
						ball.setGravity(false);
						return;
					} else if (mat == Material.OBSIDIAN && golferUuid.isPresent()) {
						Vector newVel = handleTeleporter(golferUuid.get(), golfingInfo.get(), event.getHitBlock(), event.getHitBlockFace(), vel, ball);
						if (newVel == null) { // Obsidian wasn't a teleporter
							vel.setY(-vel.getY());
						} else {
							vel = newVel;
						}
					}

					vel.setY(-vel.getY());
					vel.multiply(0.7);

					if (mat == Material.SLIME_BLOCK) {
						System.out.println("We hit slime!");
						vel.setY(vel.getY() * 1.2);
					} else {
						System.out.println("We did not hit slime... " + mat);
					}

					if (vel.getY() < 0.1) {
						vel.setY(0);
						loc.setY(Math.floor(loc.getY() * 2) / 2 + plugin.floorOffset);
						ball.teleport(loc);
						ball.setGravity(false);
					}
					break;

				default:
					break;
			}

			// Friction
			ball.setVelocity(vel);
		}
	}

	private Vector handleTeleporter(UUID golferUuid, GolfingInfo golfingInfo, Block blockHit, BlockFace faceHit, Vector vel, Snowball ball) {
		int hole = golfingInfo.getCourse().playersCurrentHole(golferUuid);
		List<Teleporters> teleporters = golfingInfo.getCourse().getHoles().get(hole).getTeleporters();
		Optional<Teleporters> teleporter = teleporters.stream().filter(t -> t.getStartingLocX() == blockHit.getX() && t.getStartingLocY() == blockHit.getY() && t.getStartingLocZ() == blockHit.getZ()).findFirst();
		if (teleporter.isEmpty()) {
			return null;
		}
		Vector newVel = Teleporters.velocityAfterTeleport(vel, faceHit, teleporter.get());
		BlockFace destinationFace = Teleporters.getConfiguredDestinationDirection(faceHit, teleporter.get());
		Vector ballOffsetForDestinationFace = Teleporters.getOffsetForDestinationFace(destinationFace, ball.getY() - ((int)ball.getY()));
		Location newLoc = teleporter.get().getDestinationBlock(ball.getWorld()).getRelative(destinationFace).getLocation().add(ballOffsetForDestinationFace);
		ball.teleport(newLoc);
		return newVel;
	}

	private static void handleIronBars(Vector vel, Snowball ball) {
		double xOffset = 0.0;
		double zOffset = 0.0;
		if (vel.getX() > 0) {
			xOffset = 0.25;
		} else if (vel.getX() < 0) {
			xOffset = -0.25;
		}
		if (vel.getZ() > 0) {
			zOffset = 0.25;
		} else if (vel.getZ() < 0) {
			zOffset = -0.25;
		}
		if (vel.getY() != 0.0) {
			ball.setGravity(true);
		}
		vel.setX(0);
		vel.setY(0);
		vel.setZ(0);

		// The iron bars are small blocks, so need to move the ball such that it's closer to the actual bars
		ball.teleport(ball.getLocation().add(xOffset, 0, zOffset));
	}
}