package com.chai.miniGolf.models;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;


import static org.bukkit.block.BlockFace.DOWN;
import static org.bukkit.block.BlockFace.EAST;
import static org.bukkit.block.BlockFace.NORTH;
import static org.bukkit.block.BlockFace.SOUTH;
import static org.bukkit.block.BlockFace.UP;
import static org.bukkit.block.BlockFace.WEST;

@Data
@Builder
@Jacksonized
public class Teleporters {
    private Integer startingLocX;
    private Integer startingLocY;
    private Integer startingLocZ;
    private Integer destinationLocX;
    private Integer destinationLocY;
    private Integer destinationLocZ;
    private BlockFace northFaceDestinationDirection;
    private BlockFace southFaceDestinationDirection;
    private BlockFace eastFaceDestinationDirection;
    private BlockFace westFaceDestinationDirection;

    public static Vector velocityAfterTeleport(Vector currentVelocity, BlockFace faceHit, Teleporters teleporters) {
        if (faceHit == BlockFace.UP || faceHit == BlockFace.DOWN) {
            return currentVelocity;
        }
        Vector newVel = currentVelocity.clone();
        BlockFace currentDirection = getDefaultDestinationDirection(faceHit);
        BlockFace destinationDirection = getConfiguredDestinationDirection(faceHit, teleporters);
        while (currentDirection != destinationDirection) {
            currentDirection = rotateCurrentDirection(currentDirection);
            newVel = rotateVelocity(newVel);
        }
        return newVel;
    }

    private static Vector rotateVelocity(Vector currentVelocity) {
        return new Vector(-currentVelocity.getZ(), currentVelocity.getY(), currentVelocity.getX());
    }

    private static BlockFace rotateCurrentDirection(BlockFace currentDirection) {
        return switch (currentDirection) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> NORTH;
        };
    }

    public static BlockFace getConfiguredDestinationDirection(BlockFace faceHit, Teleporters teleporters) {
        return switch (faceHit) {
            case NORTH -> teleporters.getNorthFaceDestinationDirection();
            case EAST -> teleporters.getEastFaceDestinationDirection();
            case SOUTH -> teleporters.getSouthFaceDestinationDirection();
            case WEST -> teleporters.getWestFaceDestinationDirection();
            case DOWN -> UP;
            case UP -> DOWN;
            default -> NORTH;
        };
    }

    @NotNull
    private static BlockFace getDefaultDestinationDirection(BlockFace faceHit) {
        return switch (faceHit) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            default -> NORTH;
        };
    }

    public static Vector getOffsetForDestinationFace(BlockFace destinationFace, double y) {
        return switch (destinationFace) {
            case NORTH -> new Vector(0.5, y, 0.9);
            case EAST -> new Vector(0.1, y, 0.5);
            case SOUTH -> new Vector(0.5, y, 0.1);
            case WEST -> new Vector(0.9, y, 0.5);
            default -> new Vector(0, y, 0);
        };
    }

    public Block getDestinationBlock(World w) {
        return w.getBlockAt(destinationLocX, destinationLocY, destinationLocZ);
    }

    public void setRule(BlockFace hitFace, BlockFace destinationFace) {
        switch (hitFace) {
            case NORTH:
                northFaceDestinationDirection = destinationFace;
                return;
            case EAST:
                eastFaceDestinationDirection = destinationFace;
                return;
            case SOUTH:
                southFaceDestinationDirection = destinationFace;
                return;
            case WEST:
                westFaceDestinationDirection = destinationFace;
                return;
        }
    }
}
