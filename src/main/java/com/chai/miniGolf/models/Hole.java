package com.chai.miniGolf.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Jacksonized
@Builder
@Data
public class Hole {
    private Integer par;
    private String worldUuid;
    private Double startingLocX;
    private Double startingLocY;
    private Double startingLocZ;
    private Float startingLocYaw;
    private Float startingLocPitch;
    private Double ballStartingLocX;
    private Double ballStartingLocY;
    private Double ballStartingLocZ;
    private Double holeLocX;
    private Double holeLocY;
    private Double holeLocZ;
    private List<Teleporters> teleporters;
    @JsonIgnore
    private Map<UUID, Integer> playerScores;

    public static Hole newHole(Integer par, Location startingLoc, Location ballStartingLoc, Location hole) {
        return Hole.builder()
            .par(par)
            .worldUuid(startingLoc.getWorld().getUID().toString())
            .startingLocX(startingLoc.getX())
            .startingLocY(startingLoc.getY())
            .startingLocZ(startingLoc.getZ())
            .startingLocYaw(startingLoc.getYaw())
            .startingLocPitch(startingLoc.getPitch())
            .ballStartingLocX(ballStartingLoc.getX())
            .ballStartingLocY(ballStartingLoc.getY())
            .ballStartingLocZ(ballStartingLoc.getZ())
            .holeLocX(hole.getX())
            .holeLocY(hole.getY())
            .holeLocZ(hole.getZ())
            .build();
    }

    @JsonIgnore
    public Location getStartingLocation() {
        return new Location(Bukkit.getWorld(UUID.fromString(worldUuid)), startingLocX, startingLocY, startingLocZ, startingLocYaw, startingLocPitch);
    }

    @JsonIgnore
    public Location getBallStartingLocation() {
        return new Location(Bukkit.getWorld(UUID.fromString(worldUuid)), ballStartingLocX, ballStartingLocY, ballStartingLocZ);
    }

    @JsonIgnore
    public Block getHoleBlock() {
        return new Location(Bukkit.getWorld(UUID.fromString(worldUuid)), holeLocX, holeLocY, holeLocZ).getBlock();
    }

    public List<Teleporters> getTeleporters() {
        if (teleporters == null) {
            teleporters = new ArrayList<>();
        }
        return teleporters;
    }

    public void createNewTeleporter(Block startBlock, Block destinationBlock) {
        if (teleporters == null) {
            teleporters = new ArrayList<>();
        }
        teleporters.add(Teleporters.builder()
                .startingLocX(startBlock.getX())
                .startingLocY(startBlock.getY())
                .startingLocZ(startBlock.getZ())
                .destinationLocX(destinationBlock.getX())
                .destinationLocY(destinationBlock.getY())
                .destinationLocZ(destinationBlock.getZ())
                .northFaceDestinationDirection(BlockFace.SOUTH)
                .southFaceDestinationDirection(BlockFace.NORTH)
                .eastFaceDestinationDirection(BlockFace.WEST)
                .westFaceDestinationDirection(BlockFace.EAST)
            .build());
    }

    public void deleteTeleporter(Block teleporterToDelete) {
        if (teleporters == null) {
            teleporters = new ArrayList<>();
        }
        List<Teleporters> teleportersToDelete = new ArrayList<>();
        for (Teleporters t : teleporters) {
            if ((t.getStartingLocX() == teleporterToDelete.getX() && t.getStartingLocY() == teleporterToDelete.getY() && t.getStartingLocZ() == teleporterToDelete.getZ()) ||
                (t.getDestinationLocX() == teleporterToDelete.getX() && t.getDestinationLocY() == teleporterToDelete.getY() && t.getDestinationLocZ() == teleporterToDelete.getZ())) {
                teleportersToDelete.add(t);
            }
        }
        teleportersToDelete.forEach(t -> teleporters.remove(t));
    }

    public void playerStartedPlayingHole(Player p) {
        playerScores().put(p.getUniqueId(), -1);
    }

    public void playerFinishedHole(Player p, int score) {
        playerScores().put(p.getUniqueId(), score);
    }

    public void playerDoneWithCourse(Player p) {
        playerScores().remove(p.getUniqueId());
    }

    public boolean hasPlayerFinishedHole(Player p) {
        Integer score = playerScores().get(p.getUniqueId());
        return score != null && score > -1;
    }

    public Integer playersScore(Player p) {
        return playerScores().get(p.getUniqueId());
    }

    @JsonIgnore
    private Map<UUID, Integer> playerScores() {
        if (playerScores == null) {
            playerScores = new HashMap<>();
        }
        return playerScores;
    }
}
