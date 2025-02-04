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

    static int width, height;
    static int[][] board; // -1 for wall, 0 for empty, 1 or 10 for known pellet

    // For distance-based decisions (e.g. super pellets)
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
                    board[y][x] = 1; // assume there's a pellet initially
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
                boolean mine = in.nextInt() != 0;
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

            // 1. Remove pellets where any pac stands
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

            // 3. Raycast to mark unseen cells as empty
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
                        if (!reportedPellets.contains(key) && board[cy][cx] != WALL) {
                            board[cy][cx] = 0;
                        }
                    }
                }
            }

            // Assign super pellets if you like
            Map<String, Integer> superPelletAssignments = assignSuperPellets(myPacs, pelletList);

            // We'll build all commands in a string builder
            StringBuilder output = new StringBuilder();

            // Keep track of collisions: cells to be occupied this turn
            // so future Pacs in the same turn won't try to occupy them.
            Set<String> reservedPositions = new HashSet<>();

            // Sort your Pacs by ID (optional but often helps stable collision resolution)
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
                    // we used SWITCH => skip movement
                    continue;
                }

                //--------------------------------------
                // 2) Possibly activate SPEED
                //--------------------------------------
                if (p.abilityCooldown == 0 && p.speedTurnsLeft == 0) {
                    // If no enemy in field of view, use SPEED
                    if (!enemyInFieldOfView(p, enemyPacs)) {
                        output.append("SPEED ").append(p.id).append(" | ");
                        // done, skip movement
                        continue;
                    }
                }

                //--------------------------------------
                // 3) Choose BFS target
                //--------------------------------------
                int[] nextSteps;
                boolean superTarget = false;

                // If assigned to a super pellet, BFS to that
                for (Map.Entry<String, Integer> entry : superPelletAssignments.entrySet()) {
                    if (entry.getValue() == p.id) {
                        String pelletKey = entry.getKey();
                        String[] parts = pelletKey.split(",");
                        int tx = Integer.parseInt(parts[0]);
                        int ty = Integer.parseInt(parts[1]);
                        nextSteps = getNextMoveConsideringSpeed(p, tx, ty, reservedPositions);
                        superTarget = true;
                        if (nextSteps.length > 0) {
                            // we found a path
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

                // if not assigned to a super pellet or no path
                if (!superTarget && !actionDone) {
                    // BFS to nearest normal pellet
                    nextSteps = getNextMoveConsideringSpeedToNearestPellet(p, reservedPositions);
                    if(p.id == 1) {
                        System.err.println("NEXT STEPS: " + Arrays.toString(nextSteps));
                    }
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

            // Debug board printing
            printBoard(board, myPacs, enemyPacs);

            // Finally output all commands
            System.out.println(output);
            System.err.println("reserved: " + reservedPositions);
        }

    }

    // ------------------------------------------------------------------------
    // Builds the "MOVE p.id finalX finalY" string from the nextSteps array.
    // nextSteps can be [x1,y1] or [x1,y1,x2,y2].
    // ------------------------------------------------------------------------
    private static void makeMoveCommand(StringBuilder output, Pac p, int[] nextSteps) {
        // final position is the last pair in nextSteps
        int finalX = nextSteps[nextSteps.length - 2];
        int finalY = nextSteps[nextSteps.length - 1];
        output.append("MOVE ").append(p.id).append(" ")
                .append(finalX).append(" ").append(finalY).append(" ").append("X: " + finalX + " Y: " + finalY)
                .append(" | ");
    }

    // ------------------------------------------------------------------------
    // BFS to get a *full path* to the nearest normal pellet. Then apply
    // speed logic (1 step or 2 steps) ignoring collisions via reservedPositions.
    // ------------------------------------------------------------------------
    static int[] getNextMoveConsideringSpeedToNearestPellet(Pac pac, Set<String> reservedPositions) {
        // BFS for nearest pellet ignoring collisions
        List<int[]> path = bfsNearestPelletFullPath(
                pac.x, pac.y, reservedPositions
        );

        if (path.size() <= 1) {
            return new int[0]; // no move
        }
        // Attempt 1 or 2 steps
        return extractNextStepsFromPath(pac, path, reservedPositions);
    }

    // ------------------------------------------------------------------------
    // BFS that returns a *full path* from (sx, sy) to the *nearest* pellet
    // ignoring "reservedPositions" as if they're walls.
    // If no pellet found, return list with only the start cell.
    // ------------------------------------------------------------------------
    static List<int[]> bfsNearestPelletFullPath(int sx, int sy, Set<String> reserved) {
        // We'll store the BFS frontier as (x,y,dist)
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[] { sx, sy, 0 });

        // For reconstructing the path
        Map<String, int[]> parent = new HashMap<>();

        // Keep track of visited to avoid cycles
        Set<String> visited = new HashSet<>();
        visited.add(sx + "," + sy);

        // If BFS finds pellets at distance d, we store them here.
        // We do NOT break right away; we gather all pellets at that distance
        List<int[]> pelletsAtDistance = new ArrayList<>();
        int foundDist = -1; // -1 means we haven't found any pellet yet.

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int cx = curr[0];
            int cy = curr[1];
            int dist = curr[2];

            // If we've already found pellets at a smaller distance,
            // we can stop as soon as we move beyond that distance.
            if (foundDist != -1 && dist > foundDist) {
                break;
            }

            // Check if there's a pellet here (but ignore if it's literally our start cell)
            if ((board[cy][cx] == 1 || board[cy][cx] == 10) && !(cx == sx && cy == sy)) {
                // If it's the first time we see a pellet, record dist as foundDist
                if (foundDist == -1) {
                    foundDist = dist;
                }
                // If this pellet is exactly at the foundDist layer, we add it
                if (dist == foundDist) {
                    pelletsAtDistance.add(new int[]{ cx, cy });
                }
                // We do NOT break yet; we want to keep exploring
                // all other cells at distance == foundDist.
            }

            // Expand neighbors
            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;

                // no vertical wrapping
                if (ny < 0 || ny >= height) continue;
                if (board[ny][nx] == WALL) continue;

                // if it's reserved, treat like a wall UNLESS it's the starting cell
                String nkey = nx + "," + ny;
                if (!(nx == sx && ny == sy) && reserved.contains(nkey)) {
                    continue; // can't go here
                }

                if (!visited.contains(nkey)) {
                    visited.add(nkey);
                    parent.put(nkey, new int[]{ cx, cy });
                    queue.add(new int[]{ nx, ny, dist + 1 });
                }
            }
        }

        // If we never found a pellet, return just our start
        if (pelletsAtDistance.isEmpty()) {
            return Collections.singletonList(new int[]{ sx, sy });
        }

        // For now, pick the *first* pellet in pelletsAtDistance
        // (You could also pick the highest-value pellet, etc.)
        int[] chosenPellet = pelletsAtDistance.get(0);

        // Reconstruct the path from (sx, sy) to the chosen pellet
        return reconstructFullPath(sx, sy, chosenPellet[0], chosenPellet[1], parent);
    }

    // ------------------------------------------------------------------------
    // BFS for a specific target ignoring "reservedPositions"
    // ------------------------------------------------------------------------
    static int[] getNextMoveConsideringSpeed(Pac pac, int tx, int ty, Set<String> reservedPositions) {
        List<int[]> path = bfsFullPathIgnoringReserved(
                pac.x, pac.y, tx, ty, reservedPositions
        );
        if (path.size() <= 1) {
            return new int[0];
        }
        return extractNextStepsFromPath(pac, path, reservedPositions);
    }

    // ------------------------------------------------------------------------
    // BFS that returns a full path from (sx, sy) to (tx, ty) ignoring collisions.
    // If no path, returns just (sx, sy).
    // ------------------------------------------------------------------------
    static List<int[]> bfsFullPathIgnoringReserved(int sx, int sy, int tx, int ty, Set<String> reserved) {
        if (sx == tx && sy == ty) {
            // already there
            return Collections.singletonList(new int[]{sx, sy});
        }
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

                // horizontal wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;

                if (ny < 0 || ny >= height) continue;
                if (board[ny][nx] == WALL) continue;

                // ignore reserved positions, except the start cell
                String nkey = nx + "," + ny;
                if (!(nx == sx && ny == sy) && reserved.contains(nkey)) {
                    continue;
                }

                if (!visited.contains(nkey)) {
                    visited.add(nkey);
                    queue.add(new int[]{nx, ny});
                    parent.put(nkey, new int[]{cx, cy});
                }
            }
        }

        if (!found) {
            return Collections.singletonList(new int[]{sx, sy});
        }

        return reconstructFullPath(sx, sy, tx, ty, parent);
    }

    // ------------------------------------------------------------------------
    // Reconstruct entire path from (sx,sy) to (tx,ty)
    // ------------------------------------------------------------------------
    static List<int[]> reconstructFullPath(int sx, int sy, int tx, int ty, Map<String, int[]> parent) {
        LinkedList<int[]> path = new LinkedList<>();
        String key = tx + "," + ty;
        path.addFirst(new int[]{tx, ty});

        while (parent.containsKey(key)) {
            int[] p = parent.get(key);
            path.addFirst(new int[]{p[0], p[1]});
            key = p[0] + "," + p[1];
        }
        return path;
    }

    // ------------------------------------------------------------------------
    // Extract next steps from BFS path (either 1 or 2 steps)
    // Then add those next positions to reservedPositions.
    // ------------------------------------------------------------------------
    static int[] extractNextStepsFromPath(Pac pac, List<int[]> path, Set<String> reservedPositions) {
        boolean hasSpeed = (pac.speedTurnsLeft > 0);
        System.err.println("PAC: " + pac.id);

        System.err.println("Has speed: " + hasSpeed);
            System.err.println("PATH: " + hasSpeed);
            for(int[] position : path) {
                System.err.println(position[0] + " " + position[1]);

        }


        // path[0] is (pac.x, pac.y)
        if (path.size() < 2) {
            return new int[0];
        }
        int[] step1 = path.get(1);
        String key1 = step1[0] + "," + step1[1];

        // If the first step is somehow already reserved, we can't move
        if (reservedPositions.contains(key1)) {
            return new int[0];
        }

        // If no speed, just do 1 step
        if (!hasSpeed) {
            reservedPositions.add(key1);
            return new int[]{ step1[0], step1[1] };
        }

        // If has speed => try 2 steps
        if (path.size() < 3) {
            // not enough steps => only 1
            reservedPositions.add(key1);
            return new int[]{ step1[0], step1[1] };
        }

        int[] step2 = path.get(2);
        String key2 = step2[0] + "," + step2[1];

        // If second step is blocked, fallback to 1
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
    // Precompute BFS distances from each cell => all others
    // (for quick super-pellet assignment, etc.)
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
        // ROCK beats SCISSORS, SCISSORS beats PAPER, PAPER beats ROCK
        switch (enemyType) {
            case "ROCK":     return "PAPER";
            case "PAPER":    return "SCISSORS";
            case "SCISSORS": return "ROCK";
        }
        return "ROCK"; // fallback
    }

    // ------------------------------------------------------------------------
    // True if there's any enemy in the same row/column (until a wall).
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

                // no vertical wrapping
                if (cy < 0 || cy >= height) break;
                if (board[cy][cx] == WALL) break;

                // check if any enemy is here
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
}
