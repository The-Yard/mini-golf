package com.chai.miniGolf.commands;

import com.chai.miniGolf.models.Course;
import com.chai.miniGolf.models.Hole;
import com.chai.miniGolf.models.Teleporters;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.chai.miniGolf.Main.getPlugin;
import static org.bukkit.Material.CAULDRON;
import static org.bukkit.Material.OBSIDIAN;

public class EditCourseCommand implements CommandExecutor, TabCompleter {
    private static final Map<UUID, Course> playersEditingCourses = new HashMap<>();
    private static final Map<String, BiFunction<String[], Player, Boolean>> editActions = Map.ofEntries(
        Map.entry("addhole", EditCourseCommand::addHole),
        Map.entry("removehole", EditCourseCommand::removeHole),
        Map.entry("setpar", EditCourseCommand::setPar),
        Map.entry("setstartinglocation", EditCourseCommand::setStartingLocation),
        Map.entry("setstartingballlocation", EditCourseCommand::setStartingBallLocation),
        Map.entry("setholelocation", EditCourseCommand::setHoleLocation),
        Map.entry("setcoursecompletionlocation", EditCourseCommand::setCourseCompletionLocation),
        Map.entry("createteleporter", EditCourseCommand::createTeleporter),
        Map.entry("editteleporter", EditCourseCommand::editTeleporter),
        Map.entry("deleteteleporter", EditCourseCommand::deleteTeleporters),
        Map.entry("doneediting", EditCourseCommand::doneEditing)
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mgop")) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " You do not have permission to run this command.");
            return true;
        }
        if (!playersEditingCourses.containsKey(((Player)sender).getUniqueId())) {
            return handleStartingToEdit((Player) sender, command, label, args);
        } else {
            return handleAlreadyEditing((Player) sender, command, label, args);
        }
    }

    private boolean handleAlreadyEditing(Player sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " You need to provide an action. Like: \"/mgedit Rolling Greens\".");
            return true;
        } else {
            BiFunction<String[], Player, Boolean> action = editActions.get(args[0]);
            if (action == null) {
                sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid action. Valid actions are: %s[%s]%s", ChatColor.WHITE, ChatColor.RED, args[0], ChatColor.WHITE, String.join(", "), ChatColor.RESET));
                return true;
            }
            return action.apply(Arrays.copyOfRange(args, 1, args.length), sender);
        }
    }

    private boolean handleStartingToEdit(Player sender, Command command, String label, String[] args) {
        if (args.length < 1 && !playersEditingCourses.containsKey(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " You need to provide a course name. Like: \"/mgedit Rolling Greens\".");
            return true;
        }
        String courseName = String.join(" ", args);
        Optional<Course> maybeCourse = getPlugin().config().getCourse(courseName);
        if (maybeCourse.isEmpty()) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + courseName + " is not a course that exists.");
            return true;
        }
        playersEditingCourses.put(sender.getUniqueId(), maybeCourse.get());
        sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" +  " Now editing: " + maybeCourse.get().getName());
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length > 0 && !playersEditingCourses.containsKey(((Player)commandSender).getUniqueId())) {
            return getPlugin().config().courses().stream().map(Course::getName)
                .filter(courseName -> courseName.toLowerCase().startsWith(String.join(" ", args).toLowerCase()))
                .map(courseName -> courseName.split(" "))
                .filter(courseNameArray -> courseNameArray.length >= args.length)
                .map(courseNameArray -> Arrays.copyOfRange(courseNameArray, args.length-1, courseNameArray.length))
                .map(courseNameArray -> String.join(" ", courseNameArray))
                .toList();
        } else if (args.length > 0) {
            Course course = playersEditingCourses.get(((Player)commandSender).getUniqueId());
            if (args.length == 1) {
                return editActions.keySet().stream().filter(a -> a.startsWith(args[0].toLowerCase())).toList();
            } else if (args.length == 2 && "addhole".equals(args[0])) {
                return IntStream.range(1, course.getHoles().size()+2).boxed().map(String::valueOf).toList();
            } else if (args.length == 2 && List.of("setpar", "setstartinglocation", "setholelocation", "setstartingballlocation", "removehole").contains(args[0]) && !course.getHoles().isEmpty()) {
                return IntStream.range(1, course.getHoles().size()+1).boxed().map(String::valueOf).toList();
            } else if (args.length == 3 && "setstartingballlocation".equals(args[0]) && !course.getHoles().isEmpty()) {
                return List.of("centered");
            } else if (args[0].equals("createteleporter")) {
                return teleporterTabComplete(args, commandSender, course);
            } else if (args[0].equals("deleteteleporter")) {
                return deleteTeleporterTabComplete(args, commandSender, course);
            } else if (args[0].equals("editteleporter")) {
                return editTeleporterTabComplete(args, commandSender, course);
            }
        }
        return List.of();
    }

    private List<String> teleporterTabComplete(String[] args, CommandSender sender, Course course) {
        Player player = (Player) sender;
        Block targ = player.getTargetBlock(null, 5);
        return switch (args.length) {
            case 2, 5 -> Collections.singletonList(targ.getX() + "");
            case 3, 6 -> Collections.singletonList(targ.getY() + "");
            case 4, 7 -> Collections.singletonList(targ.getZ() + "");
            case 8 -> IntStream.range(1, course.getHoles().size()+1).boxed().map(String::valueOf).toList();
            default -> List.of();
        };
    }

    private List<String> deleteTeleporterTabComplete(String[] args, CommandSender sender, Course course) {
        Player player = (Player) sender;
        Block targ = player.getTargetBlock(null, 5);
        return switch (args.length) {
            case 2 -> Collections.singletonList(targ.getX() + "");
            case 3 -> Collections.singletonList(targ.getY() + "");
            case 4 -> Collections.singletonList(targ.getZ() + "");
            case 5 -> IntStream.range(1, course.getHoles().size()+1).boxed().map(String::valueOf).toList();
            default -> List.of();
        };
    }

    private List<String> editTeleporterTabComplete(String[] args, CommandSender sender, Course course) {
        Player player = (Player) sender;
        Block targ = player.getTargetBlock(null, 5);
        return switch (args.length) {
            case 2 -> Collections.singletonList(targ.getX() + "");
            case 3 -> Collections.singletonList(targ.getY() + "");
            case 4 -> Collections.singletonList(targ.getZ() + "");
            case 5, 6 -> Stream.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST).map(Enum::name).filter(f -> f.startsWith(args[args.length - 1].toUpperCase())).toList();
            case 7 -> IntStream.range(1, course.getHoles().size()+1).boxed().map(String::valueOf).toList();
            default -> List.of();
        };
    }

    private static Boolean addHole(String[] args, Player sender) {
        int newHoleNumber = playersEditingCourses.get(sender.getUniqueId()).getHoles().size() + 1;
        if (args.length > 0) {
            try {
                newHoleNumber = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + args[0] + " is not a valid Hole index.");
                return true;
            }
        }
        if (newHoleNumber > playersEditingCourses.get(sender.getUniqueId()).getHoles().size() + 1) {
            sender.sendMessage(String.format("%s[MiniGolf]%s There are only %s holes, you cannot add hole number %s%s", ChatColor.WHITE, ChatColor.RED, playersEditingCourses.get(sender.getUniqueId()).getHoles().size(), newHoleNumber, ChatColor.RESET));
            return true;
        }
        sender.sendMessage(String.format("%s[MiniGolf] Adding hole at number %s, any holes behind will be pushed back a number%s", ChatColor.WHITE, newHoleNumber, ChatColor.RESET));
        Location startingLoc = sender.getLocation();
        getPlugin().config().newHoleCreated(playersEditingCourses.get(sender.getUniqueId()), newHoleNumber - 1, Hole.newHole(1, startingLoc, startingLoc, startingLoc));
        return true;
    }

    private static Boolean removeHole(String[] args, Player sender) {
        if (args.length == 0) {
            sender.sendMessage(String.format("%s[MiniGolf] You must provide a hole index to be removed%s", ChatColor.WHITE, ChatColor.RESET));
            return true;
        }
        int holeToRemoveIndex;
        try {
            holeToRemoveIndex = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + args[0] + " is not a valid Hole index.");
            return true;
        }
        if (holeToRemoveIndex > playersEditingCourses.get(sender.getUniqueId()).getHoles().size() || holeToRemoveIndex < 1) {
            sender.sendMessage(String.format("%s[MiniGolf]%s There are only %s holes, you cannot remove a hole with index %s%s", ChatColor.WHITE, ChatColor.RED, playersEditingCourses.get(sender.getUniqueId()).getHoles().size(), holeToRemoveIndex, ChatColor.RESET));
            return true;
        }
        sender.sendMessage(String.format("%s[MiniGolf] removed hole at index %s, any holes behind will be pulled forward an index%s", ChatColor.WHITE, holeToRemoveIndex, ChatColor.RESET));
        getPlugin().config().removeHole(playersEditingCourses.get(sender.getUniqueId()), holeToRemoveIndex - 1);
        return true;
    }

    private static Boolean setPar(String[] args, Player sender) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " must provide an index and a par value. Like this: \"/mgedit setpar 0 4\".");
            return true;
        }
        int holeNumber;
        int par;
        try {
            holeNumber = Integer.parseInt(args[0]);
            par = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(String.format("%s[MiniGolf]%s One of the following was not an integer: [%s, %s]%s", ChatColor.WHITE, ChatColor.RED, args[0], args[1], ChatColor.RESET));
            return true;
        }
        Course course = playersEditingCourses.get(sender.getUniqueId());
        if (holeNumber < 1 || holeNumber > course.getHoles().size()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid hole. There are %s holes.%s", ChatColor.WHITE, ChatColor.RED, holeNumber, course.getHoles().size(), ChatColor.RESET));
            return true;
        }
        sender.sendMessage(String.format("%s[MiniGolf] Setting par for hole %s to %s%s", ChatColor.WHITE, holeNumber, par, ChatColor.RESET));
        getPlugin().config().setParForHole(course, holeNumber - 1, par);
        return true;
    }

    private static Boolean setStartingLocation(String[] args, Player sender) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " Must provide a hole index to set the starting location for. Like this: \"/mgedit setstartinglocation 2\".");
            return true;
        }
        int holeNumber;
        try {
            holeNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + args[0] + " is not a valid Hole number.");
            return true;
        }
        Course course = playersEditingCourses.get(sender.getUniqueId());
        if (holeNumber < 1 || holeNumber > course.getHoles().size()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid hole. There are %s holes.%s", ChatColor.WHITE, ChatColor.RED, holeNumber, course.getHoles().size(), ChatColor.RESET));
            return true;
        }
        Location startingLoc = sender.getLocation();
        sender.sendMessage(String.format("%s[MiniGolf] Setting Starting Location for hole %s to your current location%s", ChatColor.WHITE, holeNumber, ChatColor.RESET));
        getPlugin().config().setStartingLocation(course, holeNumber - 1, startingLoc);
        return true;
    }

    private static Boolean setCourseCompletionLocation(String[] args, Player sender) {
        Course course = playersEditingCourses.get(sender.getUniqueId());
        Location completionLoc = sender.getLocation();
        sender.sendMessage(String.format("%s[MiniGolf] Setting Course Completion Location to your current location%s", ChatColor.WHITE, ChatColor.RESET));
        getPlugin().config().setCourseCompletionLocation(course, completionLoc);
        return true;
    }

    private static Boolean createTeleporter(String[] args, Player sender) {
        int x1, y1, z1, x2, y2, z2, holeNumber;
        try {
            x1 = Integer.parseInt(args[0]);
            y1 = Integer.parseInt(args[1]);
            z1 = Integer.parseInt(args[2]);
            x2 = Integer.parseInt(args[3]);
            y2 = Integer.parseInt(args[4]);
            z2 = Integer.parseInt(args[5]);
            holeNumber = Integer.parseInt(args[6]);
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " Incorrect command syntax. Syntax: \"/mgedit createteleporter <startingLocation> <destinationLocation> <holeNumber>\" Example: \"/mgedit createteleporter 103 72 -62 110 73 -63 1\".");
            return false;
        }
        Course course = playersEditingCourses.get(sender.getUniqueId());
        if (holeNumber < 1 || holeNumber > course.getHoles().size()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid hole. There are %s holes.%s", ChatColor.WHITE, ChatColor.RED, holeNumber, course.getHoles().size(), ChatColor.RESET));
            return true;
        }
        Block startBlock = sender.getWorld().getBlockAt(x1, y1, z1);
        startBlock.setType(OBSIDIAN);
        Block endBlock = sender.getWorld().getBlockAt(x2, y2, z2);
        endBlock.setType(OBSIDIAN);
        getPlugin().config().createTeleporters(course, holeNumber - 1, startBlock, endBlock);
        sender.sendMessage(String.format("%s[MiniGolf] Created Teleporters.%s", ChatColor.WHITE, ChatColor.RESET));
        return true;
    }

    private static Boolean editTeleporter(String[] args, Player sender) {
        int x, y, z, holeNumber;
        BlockFace hitFace, destinationFace;
        try {
            x = Integer.parseInt(args[0]);
            y = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
            hitFace = BlockFace.valueOf(args[3].toUpperCase());
            destinationFace = BlockFace.valueOf(args[4].toUpperCase());
            holeNumber = Integer.parseInt(args[5]);
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " Incorrect command syntax. Syntax: \"/mgedit editteleporter <destinationLocation> <hitFace> <destinationFace> <holeNumber>\" Example: \"/mgedit editteleporter 110 73 -63 NORTH WEST 1\".");
            return false;
        }
        Course course = playersEditingCourses.get(sender.getUniqueId());
        if (holeNumber < 1 || holeNumber > course.getHoles().size()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid hole. There are %s holes.%s", ChatColor.WHITE, ChatColor.RED, holeNumber, course.getHoles().size(), ChatColor.RESET));
            return true;
        }
        List<Teleporters> teleporters = course.getHoles().get(holeNumber - 1).getTeleporters().stream()
            .filter(t -> t.getDestinationLocX() == x && t.getDestinationLocY() == y && t.getDestinationLocZ() == z)
            .toList();
        if (teleporters.isEmpty()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s Could not find a teleporter who's destination is at %s %s %s.%s", ChatColor.WHITE, ChatColor.RED, x, y, z, ChatColor.RESET));
            return true;
        } else if (teleporters.size() > 1) {
            sender.sendMessage(String.format("%s[MiniGolf]%s For your info, there seems to be %s teleporters that end at this block. Only updating the rules of the first one.%s", ChatColor.WHITE, ChatColor.RED, teleporters.size(), ChatColor.RESET));
        }
        getPlugin().config().updateTeleporterRule(course, teleporters.get(0), hitFace, destinationFace);
        sender.sendMessage(String.format("%s[MiniGolf] Updated Teleporter so when it hits %s, it will now exit %s.%s", ChatColor.WHITE, hitFace, destinationFace, ChatColor.RESET));
        return true;
    }

    private static Boolean deleteTeleporters(String[] args, Player sender) {
        int x, y, z, holeNumber;
        try {
            x = Integer.parseInt(args[0]);
            y = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
            holeNumber = Integer.parseInt(args[3]);
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " Incorrect command syntax. Syntax: \"/mgedit deleteteleporters <teleporterBlockLoc> <holeNumber>\" Example: \"/mgedit deleteteleporters 103 72 -62 1\".");
            return false;
        }
        Course course = playersEditingCourses.get(sender.getUniqueId());
        if (holeNumber < 1 || holeNumber > course.getHoles().size()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid hole. There are %s holes.%s", ChatColor.WHITE, ChatColor.RED, holeNumber, course.getHoles().size(), ChatColor.RESET));
            return true;
        }
        Block block = sender.getWorld().getBlockAt(x, y, z);
        getPlugin().config().deleteTeleporters(course, holeNumber - 1, block);
        return true;
    }

    private static Boolean setStartingBallLocation(String[] args, Player sender) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " Must provide a hole index to set the starting hole location for. Like this: \"/mgedit setstartingholelocation 2\".");
            return true;
        }
        int holeNumber;
        try {
            holeNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + args[0] + " is not a valid Hole index.");
            return true;
        }
        Course course = playersEditingCourses.get(sender.getUniqueId());
        if (holeNumber < 1 || holeNumber > course.getHoles().size()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid hole. There are %s holes.%s", ChatColor.WHITE, ChatColor.RED, holeNumber, course.getHoles().size(), ChatColor.RESET));
            return true;
        }
        Location startingBallLoc = sender.getLocation();
        sender.sendMessage(String.format("%s[MiniGolf] Setting Starting ball Location for hole %s to your current location%s", ChatColor.WHITE, holeNumber, ChatColor.RESET));
        if (args.length > 1) {
            if ("centered".equals(args[1])) {
                startingBallLoc.setX(Math.ceil(startingBallLoc.getX())-0.5);
                startingBallLoc.setZ(Math.ceil(startingBallLoc.getZ())-0.5);
            }
        }
        getPlugin().config().setBallStartingLocation(course, holeNumber - 1, startingBallLoc);
        return true;
    }

    private static Boolean setHoleLocation(String[] args, Player sender) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + " Must provide a hole index to set the hole location for. Like this: \"/mgedit setholelocation 2\".");
            return true;
        }
        int holeNumber;
        try {
            holeNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.WHITE + "[MiniGolf]" + ChatColor.RED + args[0] + " is not a valid Hole index.");
            return true;
        }
        Course course = playersEditingCourses.get(sender.getUniqueId());
        if (holeNumber < 1 || holeNumber > course.getHoles().size()) {
            sender.sendMessage(String.format("%s[MiniGolf]%s %s is not a valid hole. There are %s holes.%s", ChatColor.WHITE, ChatColor.RED, holeNumber, course.getHoles().size(), ChatColor.RESET));
            return true;
        }
        Location holeLoc = sender.getLocation();
        if (!holeLoc.getBlock().getType().equals(CAULDRON)) {
            Block blockBelow = holeLoc.getBlock().getRelative(0, -1, 0);
            if (blockBelow.getType().equals(CAULDRON)) {
                holeLoc = blockBelow.getLocation();
            } else {
                sender.sendMessage(String.format("%s[MiniGolf]%s The hole must be a cauldron.%s", ChatColor.WHITE, ChatColor.RED, ChatColor.RESET));
                return true;
            }
        }
        sender.sendMessage(String.format("%s[MiniGolf] Setting Hole Location for hole %s to the cauldron beneath you%s", ChatColor.WHITE, holeNumber, ChatColor.RESET));
        getPlugin().config().setHoleLocation(course, holeNumber - 1, holeLoc);
        return true;
    }

    private static Boolean doneEditing(String[] args, Player sender) {
        sender.sendMessage(String.format("%s[MiniGolf] Finished editing \"%s\"%s", ChatColor.WHITE, playersEditingCourses.get(sender.getUniqueId()).getName(), ChatColor.RESET));
        removeEditor(sender);
        return true;
    }

    public static void removeEditor(Player editor) {
        playersEditingCourses.remove(editor.getUniqueId());
    }
}