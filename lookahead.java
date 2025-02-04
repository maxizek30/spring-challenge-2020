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
            this.x = x;
            this.y = y;
            this.value = v;
        }
    }
    static final int[][] DIRECTIONS = {
            {0, -1}, // up
            {0,  1}, // down
            {-1, 0}, // left
            {1,  0}  // right
    };
    static final int WALL = -1;
    // How far the multi-pellet BFS will look ahead
    static final int MAX_LOOKAHEAD = 7;
    static int width, height;
    static int[][] board; // -1 for wall, 0 for empty, 1 or 10 for known pellet
    // Precomputed BFS distances for use with super pellets
    static HashMap<String, HashMap<String, Integer>> preCalcDistance;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        width = in.nextInt();
        height = in.nextInt();
        board = new int[height][width];

        if (in.hasNextLine()) {
            in.nextLine();
        }

        // Read the initial map
        for (int y = 0; y < height; y++) {
            String row = in.nextLine();
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == '#') {
                    board[y][x] = WALL;
                } else {
                    // Initially assume every floor cell might have a pellet
                    board[y][x] = 1;
                }
            }
        }

        // Precompute BFS distances from every cell to every other cell (optional)
        preCalcDistance = new HashMap<>();
        precomputeDistances();

        // Game loop
        while (true) {
            int myScore = in.nextInt();
            int opponentScore = in.nextInt();

            int visiblePacCount = in.nextInt();
            List<Pac> myPacs = new ArrayList<>();
            List<Pac> enemyPacs = new ArrayList<>();

            for (int i = 0; i < visiblePacCount; i++) {
                int pacId = in.nextInt();
                boolean mine = (in.nextInt() != 0);
                int x = in.nextInt();
                int y = in.nextInt();
                String typeId = in.next();
                int speedTurnsLeft = in.nextInt();
                int abilityCooldown = in.nextInt();

                Pac p = new Pac(pacId, x, y, typeId, speedTurnsLeft, abilityCooldown);
                if (mine) {
                    myPacs.add(p);
                } else {
                    enemyPacs.add(p);
                }
            }

            int visiblePelletCount = in.nextInt();
            Set<String> reportedPellets = new HashSet<>();
            List<Pellet> pelletList = new ArrayList<>();

            for (int i = 0; i < visiblePelletCount; i++) {
                int px = in.nextInt();
                int py = in.nextInt();
                int value = in.nextInt();
                pelletList.add(new Pellet(px, py, value));
                reportedPellets.add(px + "," + py);
            }

            // 1. Remove pellets where any pac stands (both mine and enemy)
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

            // 2. Update board with newly reported pellets
            for (Pellet pel : pelletList) {
                board[pel.y][pel.x] = pel.value;
            }

            // 3. Raycast to mark unseen cells as empty (except super pellets which are visible globally)
            for (Pac pac : myPacs) {
                for (int[] dir : DIRECTIONS) {
                    int cx = pac.x;
                    int cy = pac.y;
                    while (true) {
                        cx += dir[0];
                        cy += dir[1];

                        // horizontal wrapping
                        if (cx < 0) cx = width - 1;
                        else if (cx >= width) cx = 0;

                        // no vertical wrapping
                        if (cy < 0 || cy >= height) break;
                        if (board[cy][cx] == WALL) break;

                        String key = cx + "," + cy;
                        // If not reported this turn, mark as empty
                        if (!reportedPellets.contains(key) && board[cy][cx] != WALL) {
                            board[cy][cx] = 0;
                        }
                    }
                }
            }

            // 4. Assign super pellets (optional approach)
            Map<String, Integer> superPelletAssignments = assignSuperPellets(myPacs, pelletList);

            // We'll build all commands in a string builder
            StringBuilder output = new StringBuilder();

            // Keep track of collisions: cells to be occupied this turn
            // so future Pacs won't try to stand in the same cell
            Set<String> reservedPositions = new HashSet<>();

            // (Optional) sort your Pacs by ID
            // myPacs.sort(Comparator.comparingInt(p -> p.id));

            for (Pac p : myPacs) {
                boolean actionDone = false;

                //--------------------------------------
                // 1) Possibly SWITCH to counter an enemy
                //--------------------------------------
                if (p.abilityCooldown == 0) {
                    String enemyType = checkIfEnemiesNearby(p, enemyPacs);
                    if (!enemyType.isEmpty()) {
                        // Switch to type that beats enemyType
                        String desiredType = getCounterType(enemyType);
                        if (!desiredType.equals(p.type)) {
                            output.append("SWITCH ")
                                    .append(p.id).append(" ")
                                    .append(desiredType)
                                    .append(" | ");
                            actionDone = true;
                        }
                    }
                }

                if (actionDone) {
                    // We used SWITCH => skip movement
                    continue;
                }

                //--------------------------------------
                // 2) Possibly activate SPEED
                //--------------------------------------
                if (p.abilityCooldown == 0 && p.speedTurnsLeft == 0) {
                    // For example, if no enemy is in line-of-sight, use SPEED
                    if (!enemyInFieldOfView(p, enemyPacs)) {
                        output.append("SPEED ").append(p.id).append(" | ");
                        // Done, skip movement
                        continue;
                    }
                }

                //--------------------------------------
                // 3) Move toward either a super pellet or do multi-pellet BFS
                //--------------------------------------
                int[] nextSteps;
                boolean superTarget = false;

                // If assigned to a super pellet, BFS to that
                for (Map.Entry<String, Integer> entry : superPelletAssignments.entrySet()) {
                    if (entry.getValue() == p.id) {
                        superTarget = true;
                        String pelletKey = entry.getKey();
                        String[] parts = pelletKey.split(",");
                        int tx = Integer.parseInt(parts[0]);
                        int ty = Integer.parseInt(parts[1]);

                        nextSteps = getNextMoveConsideringSpeed(p, tx, ty, reservedPositions);
                        if (nextSteps.length > 0) {
                            makeMoveCommand(output, p, nextSteps);
                            actionDone = true;
                        } else {
                            // no path => stay put
                            output.append("MOVE ").append(p.id).append(" ")
                                    .append(p.x).append(" ")
                                    .append(p.y).append(" | ");
                            actionDone = true;
                        }
                        break;
                    }
                }

                // If not assigned to a super pellet or no path to it
                if (!superTarget && !actionDone) {
                    // Our new "lookahead BFS" for normal pellets
                    nextSteps = getNextMoveConsideringSpeedToMultiPellet(p, reservedPositions);

                    if (nextSteps.length == 0) {
                        // no path => stay put
                        output.append("MOVE ").append(p.id).append(" ")
                                .append(p.x).append(" ")
                                .append(p.y).append(" | ");
                    } else {
                        makeMoveCommand(output, p, nextSteps);
                    }
                }
            }

            // Debug board printing (comment out if you want)
            printBoard(board, myPacs, enemyPacs);

            // Finally output all commands
            System.out.println(output);
            System.err.println("reserved: " + reservedPositions);
        }
    }

    // ------------------------------------------------------------------------
    // BFS that tries to collect the MOST pellets up to MAX_LOOKAHEAD steps.
    // If no pellet path found, we do a fallback BFS to "the farthest cell"
    // so the pac won't stay in place forever.
    // ------------------------------------------------------------------------
    static int[] getNextMoveConsideringSpeedToMultiPellet(Pac pac, Set<String> reservedPositions) {
        List<int[]> bestPath = bfsMultiPelletLookaheadFullPath(pac.x, pac.y, reservedPositions);

        if (bestPath.size() <= 1) {
            // Means either we can't move at all, or best path had 0 pellets
            return new int[0];
        }
        return extractNextStepsFromPath(pac, bestPath, reservedPositions);
    }

    static List<int[]> bfsMultiPelletLookaheadFullPath(int sx, int sy, Set<String> reserved) {
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{ sx, sy, 0 }); // (x, y, dist)
        Set<String> visited = new HashSet<>();
        visited.add(sx + "," + sy);

        // For reconstructing any path
        Map<String, String> parent = new HashMap<>();

        // We'll keep track of all visited cells within BFS
        List<int[]> visitedCells = new ArrayList<>();
        visitedCells.add(new int[]{sx, sy});

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int cx = curr[0], cy = curr[1], dist = curr[2];

            if (dist >= MAX_LOOKAHEAD) {
                // Don’t expand further
                continue;
            }

            // Expand neighbors
            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;

                // vertical boundary check
                if (ny < 0 || ny >= height) continue;
                if (board[ny][nx] == WALL) continue;

                String nkey = nx + "," + ny;
                // if it's reserved, treat like a wall (unless it's the start)
                if (!(nx == sx && ny == sy) && reserved.contains(nkey)) {
                    continue;
                }

                if (!visited.contains(nkey)) {
                    visited.add(nkey);
                    visitedCells.add(new int[]{nx, ny});
                    parent.put(nkey, cx + "," + cy);
                    queue.add(new int[]{nx, ny, dist + 1});
                }
            }
        }

        // Now pick the visited cell that yields the highest pellet sum on the path
        int bestPelletSum = 0;
        List<int[]> bestPath = Collections.singletonList(new int[]{sx, sy});

        for (int[] cell : visitedCells) {
            int cx = cell[0];
            int cy = cell[1];
            // Reconstruct path from (sx, sy) to (cx, cy)
            List<int[]> path = reconstructFullPath(sx, sy, cx, cy, parent);

            int pelletSum = 0;
            for (int[] pp : path) {
                int px = pp[0], py = pp[1];
                if (board[py][px] == 1) pelletSum += 1;
                else if (board[py][px] == 10) pelletSum += 10;
            }

            if (pelletSum > bestPelletSum) {
                bestPelletSum = pelletSum;
                bestPath = path;
            }
        }

        // If bestPelletSum == 0, we do a fallback BFS to some far cell.
        // Because otherwise we'd just stay put in the corner.
        if (bestPelletSum == 0) {
            // fallback BFS: find the farthest reachable cell ignoring collisions
            List<int[]> fallbackPath = fallbackFarthestCellBFS(sx, sy, reserved);
            if (fallbackPath.size() > 1) {
                return fallbackPath;
            }
        }

        return bestPath;
    }

    // ------------------------------------------------------------------------
    // A BFS that finds the farthest reachable cell from (sx, sy),
    // ignoring collisions. Then reconstructs a path to it.
    // ------------------------------------------------------------------------
    static List<int[]> fallbackFarthestCellBFS(int sx, int sy, Set<String> reserved) {
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{sx, sy, 0});
        Set<String> visited = new HashSet<>();
        visited.add(sx + "," + sy);

        Map<String, String> parent = new HashMap<>();
        int maxDist = 0;
        int farthestX = sx, farthestY = sy;

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int cx = curr[0], cy = curr[1], dist = curr[2];

            // track the farthest cell
            if (dist > maxDist) {
                maxDist = dist;
                farthestX = cx;
                farthestY = cy;
            }

            // explore neighbors
            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // horizontal wrap
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height) continue;
                if (board[ny][nx] == WALL) continue;

                String nkey = nx + "," + ny;
                // ignore reserved unless it's the start
                if (!(nx == sx && ny == sy) && reserved.contains(nkey)) {
                    continue;
                }

                if (!visited.contains(nkey)) {
                    visited.add(nkey);
                    parent.put(nkey, cx + "," + cy);
                    queue.add(new int[]{nx, ny, dist + 1});
                }
            }
        }

        // Reconstruct path from (sx, sy) to farthestX, farthestY
        return reconstructFullPath(sx, sy, farthestX, farthestY, parent);
    }

    // ------------------------------------------------------------------------
    // BFS for a *specific target* ignoring collisions
    // ------------------------------------------------------------------------
    static int[] getNextMoveConsideringSpeed(Pac pac, int tx, int ty, Set<String> reservedPositions) {
        List<int[]> path = bfsFullPathIgnoringReserved(pac.x, pac.y, tx, ty, reservedPositions);
        if (path.size() <= 1) {
            return new int[0];
        }
        return extractNextStepsFromPath(pac, path, reservedPositions);
    }

    // ------------------------------------------------------------------------
    // BFS ignoring collisions to find path from (sx, sy) to (tx, ty).
    // If none, returns just [sx, sy].
    // ------------------------------------------------------------------------
    static List<int[]> bfsFullPathIgnoringReserved(int sx, int sy, int tx, int ty, Set<String> reserved) {
        if (sx == tx && sy == ty) {
            return Collections.singletonList(new int[]{sx, sy});
        }
        Queue<int[]> queue = new LinkedList<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(new int[]{sx, sy});
        visited.add(sx + "," + sy);

        boolean found = false;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0], cy = cur[1];

            if (cx == tx && cy == ty) {
                found = true;
                break;
            }

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // wrap horizontally
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;

                if (ny < 0 || ny >= height) continue;
                if (board[ny][nx] == WALL) continue;

                String nkey = nx + "," + ny;
                // ignore reserved positions (unless it's the start cell)
                if (!(nx == sx && ny == sy) && reserved.contains(nkey)) {
                    continue;
                }

                if (!visited.contains(nkey)) {
                    visited.add(nkey);
                    parent.put(nkey, cx + "," + cy);
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        if (!found) {
            // no path
            return Collections.singletonList(new int[]{sx, sy});
        }

        return reconstructFullPath(sx, sy, tx, ty, parent);
    }

    // ------------------------------------------------------------------------
    // Reconstruct entire path from (sx, sy) to (tx, ty) via parent's map
    // ------------------------------------------------------------------------
    static List<int[]> reconstructFullPath(int sx, int sy, int tx, int ty, Map<String, String> parent) {
        LinkedList<int[]> path = new LinkedList<>();
        String key = tx + "," + ty;
        path.addFirst(new int[]{tx, ty});

        while (parent.containsKey(key)) {
            String pKey = parent.get(key);
            String[] parts = pKey.split(",");
            int px = Integer.parseInt(parts[0]);
            int py = Integer.parseInt(parts[1]);
            path.addFirst(new int[]{px, py});
            key = pKey;
        }
        return path;
    }

    // ------------------------------------------------------------------------
    // Extract next steps from BFS path (either 1 step or 2 steps if Pac has SPEED),
    // and mark those cells as reserved.
    // ------------------------------------------------------------------------
    static int[] extractNextStepsFromPath(Pac pac, List<int[]> path, Set<String> reservedPositions) {
        boolean hasSpeed = (pac.speedTurnsLeft > 0);

        // path[0] is the Pac's current position
        if (path.size() < 2) {
            return new int[0];
        }
        int[] step1 = path.get(1);
        String key1 = step1[0] + "," + step1[1];

        // If the first step is already reserved, we can't move
        if (reservedPositions.contains(key1)) {
            return new int[0];
        }

        // If no speed, do just 1 step
        if (!hasSpeed) {
            reservedPositions.add(key1);
            return new int[]{ step1[0], step1[1] };
        }

        // If has speed => try 2 steps
        if (path.size() < 3) {
            // Not enough steps => only 1
            reservedPositions.add(key1);
            return new int[]{ step1[0], step1[1] };
        }

        int[] step2 = path.get(2);
        String key2 = step2[0] + "," + step2[1];

        // If second step is blocked/reserved, fallback to 1 step
        if (reservedPositions.contains(key2)) {
            reservedPositions.add(key1);
            return new int[]{ step1[0], step1[1] };
        }

        // Both free => move 2 cells
        reservedPositions.add(key1);
        reservedPositions.add(key2);
        return new int[]{ step1[0], step1[1], step2[0], step2[1] };
    }

    // ------------------------------------------------------------------------
    // Precompute BFS distances from each cell => all others (optional)
    // ------------------------------------------------------------------------
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

    // Standard BFS to fill a distance map from (sx, sy) to all reachable cells
    static HashMap<String, Integer> bfsDistancesFrom(int sx, int sy) {
        HashMap<String, Integer> distMap = new HashMap<>();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{sx, sy, 0});
        distMap.put(sx + "," + sy, 0);

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cy = current[1], d = current[2];

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height) continue;
                if (board[ny][nx] == WALL) continue;

                String key = nx + "," + ny;
                if (!distMap.containsKey(key)) {
                    distMap.put(key, d + 1);
                    queue.add(new int[]{nx, ny, d + 1});
                }
            }
        }
        return distMap;
    }

    // ------------------------------------------------------------------------
    // Assign each super pellet to the closest Pac (simple approach).
    // ------------------------------------------------------------------------
    static Map<String, Integer> assignSuperPellets(List<Pac> myPacs, List<Pellet> pelletList) {
        Map<String, Integer> assignments = new HashMap<>();

        for (Pellet pel : pelletList) {
            if (pel.value == 10) {
                String pelletKey = pel.x + "," + pel.y;
                int bestPacId = -1;
                int bestDist = Integer.MAX_VALUE;

                for (Pac p : myPacs) {
                    String pk = p.x + "," + p.y;
                    if (preCalcDistance.containsKey(pk)) {
                        HashMap<String, Integer> dm = preCalcDistance.get(pk);
                        if (dm.containsKey(pelletKey)) {
                            int d = dm.get(pelletKey);
                            if (d < bestDist) {
                                bestDist = d;
                                bestPacId = p.id;
                            }
                        }
                    }
                }

                if (bestPacId != -1) {
                    assignments.put(pelletKey, bestPacId);
                }
            }
        }
        return assignments;
    }

    // ------------------------------------------------------------------------
    // Check if an enemy is within ~2 cells => if so return that enemy's type
    // ------------------------------------------------------------------------
    static String checkIfEnemiesNearby(Pac p, List<Pac> enemies) {
        for (Pac e : enemies) {
            // Manhattan distance check
            int dist = Math.abs(p.x - e.x) + Math.abs(p.y - e.y);
            if (dist <= 2) {
                return e.type;
            }
        }
        return "";
    }

    // ------------------------------------------------------------------------
    // Decide a type that beats enemyType
    // ------------------------------------------------------------------------
    static String getCounterType(String enemyType) {
        // ROCK beats SCISSORS
        // SCISSORS beats PAPER
        // PAPER beats ROCK
        switch (enemyType) {
            case "ROCK":     return "PAPER";
            case "PAPER":    return "SCISSORS";
            case "SCISSORS": return "ROCK";
        }
        return "ROCK"; // fallback
    }

    // ------------------------------------------------------------------------
    // Check if there's any enemy in the same row/column (until a wall).
    // A sample approach for deciding e.g. to skip SPEED if an enemy is near
    // ------------------------------------------------------------------------
    static boolean enemyInFieldOfView(Pac p, List<Pac> enemyPacs) {
        for (int[] dir : DIRECTIONS) {
            int cx = p.x;
            int cy = p.y;
            while (true) {
                cx += dir[0];
                cy += dir[1];

                // horizontal wrapping
                if (cx < 0) cx = width - 1;
                else if (cx >= width) cx = 0;

                if (cy < 0 || cy >= height) break;
                if (board[cy][cx] == WALL) break;

                for (Pac e : enemyPacs) {
                    if (e.x == cx && e.y == cy) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Debug print of the board with emojis
    // ------------------------------------------------------------------------
    static void printBoard(int[][] board, List<Pac> myPacs, List<Pac> enemyPacs) {
        for (int i = 0; i < board.length; i++) {
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < board[0].length; j++) {
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

                if (isMyPac) {
                    row.append("🟥");
                } else if (isEnemyPac) {
                    row.append("🟦");
                } else if (board[i][j] == WALL) {
                    row.append("🟫");
                } else if (board[i][j] == 10) {
                    row.append("💰");
                } else if (board[i][j] == 1) {
                    row.append("⚪");
                } else {
                    row.append("⬛");
                }
            }
            System.err.println(row);
        }
    }

    // ------------------------------------------------------------------------
    // Helper to build a "MOVE" command from nextSteps
    // nextSteps can be [x1,y1] or [x1,y1,x2,y2].
    // ------------------------------------------------------------------------
    private static void makeMoveCommand(StringBuilder output, Pac p, int[] nextSteps) {
        // final position is the last pair in nextSteps
        int finalX = nextSteps[nextSteps.length - 2];
        int finalY = nextSteps[nextSteps.length - 1];
        output.append("MOVE ").append(p.id).append(" ")
                .append(finalX).append(" ").append(finalY)
                .append(" X:").append(finalX).append("Y:").append(finalY)
                .append(" | ");
    }
}
