import java.util.*;

/**
 * Grab the pellets as fast as you can!
 **/
class Player {
    static class Pac {
        int id, x, y;
        String type;
        int speedTurnsLeft;
        int abilityCooldown;


        Pac(int id, int x, int y, String type, int speedTurnsLeft, int abilityCooldown) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.type = type;
            this.speedTurnsLeft = speedTurnsLeft;
            this.abilityCooldown = abilityCooldown;
        }
    }
    static class Pellet {
        int x, y, value; // 1 or 10
        Pellet(int x, int y, int v) {
            this.x = x; this.y = y; this.value = v;
        }
    }
    static final int[][] DIRECTIONS = {
            {0, -1}, // up
            {0,  1}, // down
            {-1, 0}, // left
            {1,  0}  // right
    };
    static int[][] board; // -1 for wall, 0 for empty, 1 or 10 for known pellet
    static final int WALL = -1;
    static int width, height;
    static HashMap<String, HashMap<String, Integer>> preCalcDistance;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        width = in.nextInt();
        height = in.nextInt();
        board = new int[height][width];
        if (in.hasNextLine()) {
            in.nextLine();
        }
        //read the initial map
        for (int y = 0; y < height; y++) {
            String row = in.nextLine();
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == '#') {
                    board[y][x] = WALL;
                } else {
                    // floor => presume there's a pellet (value 1)
                    board[y][x] = 1;
                }
            }
        }
        //pre calculate the distance from any cell to any other cell using BFS
        preCalcDistance = new HashMap<>();
        precomputeDistances();


        // game loop
        while (true) {
            int myScore = in.nextInt();
            int opponentScore = in.nextInt();
            int visiblePacCount = in.nextInt(); // all your pacs and enemy pacs in sight
            List<Pac> myPacs = new ArrayList<>();
            List<Pac> enemyPacs = new ArrayList<>();
            for (int i = 0; i < visiblePacCount; i++) {
                int pacId = in.nextInt(); // pac number (unique within a team)
                boolean mine = in.nextInt() != 0; // true if this pac is yours
                int x = in.nextInt(); // position in the grid
                int y = in.nextInt(); // position in the grid
                String typeId = in.next(); // ROCK/PAPER/SCISSORS
                int speedTurnsLeft = in.nextInt(); // unused in wood leagues
                int abilityCooldown = in.nextInt(); // unused in wood leagues
                if (mine) {
                    myPacs.add(new Pac(pacId, x, y, typeId, speedTurnsLeft, abilityCooldown));
                } else {
                    enemyPacs.add(new Pac(pacId, x, y, typeId, speedTurnsLeft, abilityCooldown));
                }
            }
            int visiblePelletCount = in.nextInt(); // all pellets in sight

            Set<String> reportedPellets = new HashSet<>();

            List<Pellet> pelletList = new ArrayList<>();
            for (int i = 0; i < visiblePelletCount; i++) {
                int x = in.nextInt();
                int y = in.nextInt();
                int value = in.nextInt(); // amount of points this pellet is worth
                pelletList.add(new Pellet(x,y,value));
                reportedPellets.add(x + "," + y);
            }
            // --- Update pellet info ---
            // 1. Remove pellets where a pac stands (both your pac or enemy pac)
            for (Pac p : myPacs) {
                if (board[p.y][p.x] != WALL) {
                    board[p.y][p.x] = 0;
                }
            }
            for (Pac p : enemyPacs) {
                if (board[p.y][p.x] != WALL) {
                    board[p.y][p.x] = 0;
                }
            }
            // --- 3) For each REPORTED pellet, set board[y][x] = value (1 or 10) ---
            for (Pellet pel : pelletList) {
                board[pel.y][pel.x] = pel.value;
            }
            // get a list of visible pellets
            for(Pac pac : myPacs) {
                int cx = pac.x;
                int cy = pac.y;
                for(int[] dir : DIRECTIONS) {
                    while(true) {
                        cx += dir[0];
                        cy += dir[1];

                        // Handle horizontal wrapping
                        if (cx < 0) cx = board[0].length - 1;
                        else if (cx >= board[0].length) cx = 0;

                        //no vertical wrapping, so stop if out of bounds:
                        if (cy < 0 || cy >= board.length) {
                            break;
                        }

                        // If we hit a wall, stop the ray
                        if (board[cy][cx] == WALL) {
                            break;
                        }
                        // Otherwise, this cell is visible and check if pellet exists
                        if(!reportedPellets.contains(cx + "," + cy) && board[cy][cx] != WALL) {
                            board[cy][cx] = 0;
                        }
                    }
                }
            }
            StringBuilder output = new StringBuilder();
            Map<String, Integer> superPelletAssignments = assignSuperPellets(myPacs, pelletList);
            Set<String> reservedPositions = new HashSet<>();

            for (Pac p : myPacs) {
                boolean actionDone = false;

                if (p.abilityCooldown == 0) {
                    // If an enemy is within distance ~2, consider a SWITCH to counter
                    String enemyTypeCloseBy = checkIfEnemiesNearby(p, enemyPacs);
                    if (!enemyTypeCloseBy.isEmpty()) {
                        // Try to SWITCH to beat enemy type
                        String desiredType = getCounterType(enemyTypeCloseBy);
                        if (!desiredType.equals(p.type)) {
                            // We do a SWITCH
                            output.append("SWITCH ").append(p.id).append(" ").append(desiredType).append(" | ");
                            actionDone = true;
                        }
                    }
                }

                if (actionDone) {
                    // We used our turn to SWITCH. Move on to the next Pac
                    continue;
                }
                // ------------------------------------------------
                // 2) Possibly activate SPEED if no enemy in sight
                // ------------------------------------------------
                if (p.abilityCooldown == 0 && p.speedTurnsLeft == 0) {
                    // Check if we see any enemy in the visible row/column
                    if (!enemyInFieldOfView(p, enemyPacs)) {
                        // Use speed
                        output.append("SPEED ").append(p.id).append(" | ");
                        // That uses up this turn, so skip moving
                        continue;
                    }
                }
                // ------------------------------------------------
                // 3) Decide on a target (super pellet or nearest pellet)
                // ------------------------------------------------
                int[] nextMoveSteps = new int[]{2}; // This will store either 2 or 4 integers [x1, y1(, x2, y2)]
                boolean isTargetingSuperPellet = false;
                // Check if this Pac is assigned to a super pellet
                for (Map.Entry<String, Integer> entry : superPelletAssignments.entrySet()) {
                    if (entry.getValue() == p.id) {
                        String pelletKey = entry.getKey();
                        String[] parts = pelletKey.split(",");
                        int targetX = Integer.parseInt(parts[0]);
                        int targetY = Integer.parseInt(parts[1]);
                        // BFS path to that super pellet
                        nextMoveSteps = getNextMoveConsideringSpeed(p, targetX, targetY, reservedPositions);
                        isTargetingSuperPellet = true;
                        break;
                    }
                }
                // If not assigned to a super pellet, go for nearest normal pellet
                if (!isTargetingSuperPellet) {
                    nextMoveSteps = getNextMoveConsideringSpeedToNearestPellet(p, reservedPositions);
                }
                // nextMoveSteps can be either [x1, y1] if not using speed
                // or [x1, y1, x2, y2] if using speed (and we found 2 valid cells).
                // The final coordinate is the last pair in nextMoveSteps.
                // If nextMoveSteps is empty, we just stay put.
                if (nextMoveSteps.length == 0) {
                    // No movement possible
                    output.append("MOVE ").append(p.id).append(" ").append(p.x).append(" ").append(p.y).append(" | ");
                } else {
                    int finalX = nextMoveSteps[nextMoveSteps.length - 2];
                    int finalY = nextMoveSteps[nextMoveSteps.length - 1];
                    output.append("MOVE ").append(p.id).append(" ").append(finalX).append(" ").append(finalY).append(" | ");
                }
            }
            printBoard(board, myPacs, enemyPacs);
            System.out.println(output);
        }

    }
    // --------------------------------------------------------------------------------
    // BFS to find nearest normal pellet. Then retrieve either 1 or 2 steps
    // based on whether pac has speed or not. Also checks collisions via reservedPositions.
    // --------------------------------------------------------------------------------
    static int[] getNextMoveConsideringSpeedToNearestPellet(Pac pac, Set<String> reservedPositions) {
        // BFS for the nearest pellet
        // Then reconstruct the full path.
        List<int[]> path = bfsNearestPelletFullPath(pac.x, pac.y);
        if (path.size() <= 1) {
            // Either no path or only the start cell => no move
            return new int[0];
        }
        // Attempt 1 or 2 steps
        return extractNextStepsFromPath(pac, path, reservedPositions);
    }
    // --------------------------------------------------------------------------------
    // BFS that returns the *full path* from (sx, sy) to the *nearest* pellet (1 or 10).
    // If no pellet found, returns a path of just { (sx, sy) }.
    // --------------------------------------------------------------------------------
    static List<int[]> bfsNearestPelletFullPath(int sx, int sy) {
        Queue<int[]> queue = new LinkedList<>();
        Map<String, int[]> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(new int[]{sx, sy});
        visited.add(sx + "," + sy);

        int[] foundGoal = null;

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0];
            int cy = current[1];

            // If this cell has a pellet
            if (board[cy][cx] == 1 || board[cy][cx] == 10) {
                foundGoal = current;
                break;
            }

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // Horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height) continue; // no vertical wrapping
                if (board[ny][nx] == WALL) continue;

                String key = nx + "," + ny;
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(new int[]{nx, ny});
                    parent.put(key, new int[]{cx, cy});
                }
            }
        }

        // If no pellet found, return just the start as path
        if (foundGoal == null) {
            return Collections.singletonList(new int[]{sx, sy});
        }
        // Reconstruct path from foundGoal back to start
        return reconstructFullPath(sx, sy, foundGoal[0], foundGoal[1], parent);
    }
    static int[] bfsNearestPellet(Pac pac) {
        Queue<int[]> queue = new LinkedList<>();
        Map<String, int[]> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(new int[]{pac.x, pac.y});
        visited.add(pac.x + "," + pac.y);

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cy = current[1];

            // If we find a pellet, reconstruct the first move toward it
            if (board[cy][cx] == 1 || board[cy][cx] == 10) {
                return reconstructPath(pac.x, pac.y, cx, cy, parent);
            }

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // Handle horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height || board[ny][nx] == WALL) continue;

                String key = nx + "," + ny;
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(new int[]{nx, ny});
                    parent.put(key, new int[]{cx, cy});
                }
            }
        }

        // If no pellet is found (which shouldn't happen), stay in place
        return new int[]{pac.x, pac.y};
    }

    static int[] reconstructPath(int startX, int startY, int targetX, int targetY, Map<String, int[]> parent) {
        String key = targetX + "," + targetY;
        while (parent.containsKey(key)) {
            int[] prev = parent.get(key);
            if (prev[0] == startX && prev[1] == startY) {
                return new int[]{targetX, targetY};
            }
            key = prev[0] + "," + prev[1];
            targetX = prev[0];
            targetY = prev[1];
        }
        return new int[]{startX, startY};
    }
    static void printBoard(int[][] board, List<Pac> myPacs, List<Pac> enemyPacs) {
        for (int i = 0; i < board.length; i++) {
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < board[0].length; j++) {
                // Check if a Pac is here
                boolean isMyPac = false;
                boolean isEnemyPac = false;

                for (Pac p : myPacs) {
                    if (p.y == i && p.x == j) {
                        isMyPac = true;
                        break;
                    }
                }

                for (Pac e : enemyPacs) {
                    if (e.y == i && e.x == j) {
                        isEnemyPac = true;
                        break;
                    }
                }

                // Assign appropriate emoji
                if (isMyPac) {
                    row.append("🟥"); // Your Pac
                } else if (isEnemyPac) {
                    row.append("🟦"); // Enemy Pac
                } else if (board[i][j] == -1) {
                    row.append("🟫"); // Wall
                } else if (board[i][j] == 10) {
                    row.append("💰"); // Super Pellet
                } else if (board[i][j] == 1) {
                    row.append("⚪"); // Normal Pellet
                } else {
                    row.append("⬛"); // Empty Floor (no pellet)
                }
            }
            System.err.println(row);
        }
    }


    static String checkIfEnemiesNearby(Pac p, List<Pac> enemies) {
        for (Pac enemy : enemies) {
            int distance = Math.abs(p.x - enemy.x) + Math.abs(p.y - enemy.y);
            if(distance <= 2) {
                return enemy.type;
            }
        }
        return "";
    }
    static void precomputeDistances() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (board[y][x] != WALL) {
                    String startKey = x + "," + y;
                    preCalcDistance.put(startKey, bfsDistancesFrom(x, y));
                }
            }
        }
    }

    static HashMap<String, Integer> bfsDistancesFrom(int startX, int startY) {
        HashMap<String, Integer> distances = new HashMap<>();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startX, startY, 0}); // {x, y, distance}
        distances.put(startX + "," + startY, 0);

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cy = current[1], dist = current[2];

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // Handle horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height || board[ny][nx] == WALL) continue; // No vertical wrapping

                String key = nx + "," + ny;
                if (!distances.containsKey(key)) {
                    distances.put(key, dist + 1);
                    queue.add(new int[]{nx, ny, dist + 1});
                }
            }
        }
        return distances;
    }
    static int[] bfsTowardsTarget(Pac pac, int targetX, int targetY) {
        Queue<int[]> queue = new LinkedList<>();
        Map<String, int[]> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(new int[]{pac.x, pac.y});
        visited.add(pac.x + "," + pac.y);

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cy = current[1];

            // If we reach the target super pellet, reconstruct the first move
            if (cx == targetX && cy == targetY) {
                return reconstructPath(pac.x, pac.y, targetX, targetY, parent);
            }

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // Handle horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height || board[ny][nx] == WALL) continue;

                String key = nx + "," + ny;
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(new int[]{nx, ny});
                    parent.put(key, new int[]{cx, cy});
                }
            }
        }

        // If something goes wrong, stay in place
        return new int[]{pac.x, pac.y};
    }
    static Map<String, Integer> assignSuperPellets(List<Pac> myPacs, List<Pellet> pelletList) {
        Map<String, Integer> superPelletAssignments = new HashMap<>();
        List<int[]> superPellets = new ArrayList<>();

        // Collect super pellet locations
        for (Pellet pellet : pelletList) {
            if (pellet.value == 10) {
                superPellets.add(new int[]{pellet.x, pellet.y});
            }
        }

        // Assign the closest Pac to each super pellet
        for (int[] pellet : superPellets) {
            String pelletKey = pellet[0] + "," + pellet[1];
            int closestPacId = -1;
            int minDist = Integer.MAX_VALUE;

            for (Pac pac : myPacs) {
                String pacKey = pac.x + "," + pac.y;
                if (preCalcDistance.containsKey(pacKey) && preCalcDistance.get(pacKey).containsKey(pelletKey)) {
                    int dist = preCalcDistance.get(pacKey).get(pelletKey);
                    if (dist < minDist) {
                        minDist = dist;
                        closestPacId = pac.id;
                    }
                }
            }

            // Assign the closest Pac to the super pellet
            if (closestPacId != -1) {
                superPelletAssignments.put(pelletKey, closestPacId);
            }
        }

        return superPelletAssignments;
    }
    // --------------------------------------------------------------------------------
    // BFS to a specific target (targetX,targetY). Then retrieve either 1 or 2 steps
    // based on whether pac has speed or not. Also checks collisions via reservedPositions.
    // --------------------------------------------------------------------------------
    static int[] getNextMoveConsideringSpeed(Pac pac, int targetX, int targetY, Set<String> reservedPositions) {
        // BFS for a path from (pac.x, pac.y) to (targetX, targetY)
        List<int[]> path = bfsFullPath(pac.x, pac.y, targetX, targetY);
        if (path.size() <= 1) {
            // No path or only start cell
            return new int[0];
        }
        return extractNextStepsFromPath(pac, path, reservedPositions);
    }

    // --------------------------------------------------------------------------------
    // This helper tries to reserve 2 steps if speedTurnsLeft > 0, else 1 step.
    // If the second step is blocked, fallback to 1 step. If the first step is blocked,
    // fallback to 0 steps.
    // Returns an int[] of length 2 if 1 step, length 4 if 2 steps, or length 0 if no move.
    // --------------------------------------------------------------------------------
    static int[] extractNextStepsFromPath(Pac pac, List<int[]> path, Set<String> reservedPositions) {
        // path[0] = (pac.x, pac.y)
        // path[1] = next cell
        // path[2] = second next cell, etc.
        boolean hasSpeed = (pac.speedTurnsLeft > 0);

        // The next cell
        if (path.size() < 2) {
            return new int[0];
        }
        int[] firstStep = path.get(1);
        String firstKey = firstStep[0] + "," + firstStep[1];

        // Check if first step is free
        if (reservedPositions.contains(firstKey)) {
            // Colliding => stay put
            return new int[0];
        }

        // If we do NOT have speed, just reserve that first step
        if (!hasSpeed) {
            reservedPositions.add(firstKey);
            return new int[]{ firstStep[0], firstStep[1] };
        }

        // If we have speed => try to take 2 steps
        if (path.size() < 3) {
            // Not enough steps in path, so only 1 step is possible
            reservedPositions.add(firstKey);
            return new int[]{ firstStep[0], firstStep[1] };
        }

        int[] secondStep = path.get(2);
        String secondKey = secondStep[0] + "," + secondStep[1];

        if (reservedPositions.contains(secondKey)) {
            // We cannot safely move 2 steps, fallback to 1
            reservedPositions.add(firstKey);
            return new int[]{ firstStep[0], firstStep[1] };
        }

        // If both steps are free, reserve them both
        reservedPositions.add(firstKey);
        reservedPositions.add(secondKey);
        // Return 2 steps
        return new int[]{ firstStep[0], firstStep[1], secondStep[0], secondStep[1] };
    }
    // --------------------------------------------------------------------------------
    // BFS that returns the full path from (sx, sy) to (tx, ty) if possible.
    // If no path found, return just { (sx, sy) }.
    // --------------------------------------------------------------------------------
    static List<int[]> bfsFullPath(int sx, int sy, int tx, int ty) {
        Queue<int[]> queue = new LinkedList<>();
        Map<String, int[]> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(new int[]{sx, sy});
        visited.add(sx + "," + sy);

        boolean found = false;

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cy = current[1];

            if (cx == tx && cy == ty) {
                found = true;
                break;
            }

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // Horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height) continue;
                if (board[ny][nx] == WALL) continue;

                String key = nx + "," + ny;
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(new int[]{nx, ny});
                    parent.put(key, new int[]{cx, cy});
                }
            }
        }

        if (!found) {
            // No path => return just start
            return Collections.singletonList(new int[]{sx, sy});
        }
        // Reconstruct path
        return reconstructFullPath(sx, sy, tx, ty, parent);
    }
    // --------------------------------------------------------------------------------
    // Reconstruct the entire path from (sx, sy) to (tx, ty) using parent map
    // The result includes both start and target in the list.
    // --------------------------------------------------------------------------------
    static List<int[]> reconstructFullPath(int sx, int sy, int tx, int ty, Map<String, int[]> parent) {
        LinkedList<int[]> path = new LinkedList<>();
        String currentKey = tx + "," + ty;
        path.addFirst(new int[]{tx, ty});
        while (parent.containsKey(currentKey)) {
            int[] p = parent.get(currentKey);
            path.addFirst(new int[]{p[0], p[1]});
            currentKey = p[0] + "," + p[1];
        }
        return path;
    }
    static String getCounterType(String enemyType) {
        // ROCK beats SCISSORS, SCISSORS beats PAPER, PAPER beats ROCK
        switch (enemyType) {
            case "ROCK":     return "PAPER";
            case "PAPER":    return "SCISSORS";
            case "SCISSORS": return "ROCK";
        }
        // default fallback
        return "ROCK";
    }
    static boolean enemyInFieldOfView(Pac p, List<Pac> enemyPacs) {
        // For each direction, raycast and see if we hit an enemy
        for (int[] dir : DIRECTIONS) {
            int cx = p.x;
            int cy = p.y;
            while (true) {
                cx += dir[0];
                cy += dir[1];

                // Horizontal wrapping
                if (cx < 0) cx = width - 1;
                else if (cx >= width) cx = 0;

                // No vertical wrapping
                if (cy < 0 || cy >= height) break;
                if (board[cy][cx] == WALL) break;

                // See if there's an enemy here
                for (Pac e : enemyPacs) {
                    if (e.x == cx && e.y == cy) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}