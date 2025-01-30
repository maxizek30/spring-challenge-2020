import java.util.*;
import java.io.*;
import java.math.*;

/**
 * Grab the pellets as fast as you can!
 **/
class Player {

    static final int[][] DIRECTIONS = {
            {0, -1}, // up
            {0,  1}, // down
            {-1, 0}, // left
            {1,  0}  // right
    };
    static int[][] board; // -1 for wall, 0 for empty, 1 or 10 for known pellet
    static final int WALL = -1;
    // We'll use -2 for "reserved" cells so no other pac picks them

    static final int RESERVED = -2;

    static int width, height;

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
                    myPacs.add(new Pac(pacId, x, y));
                } else {
                    enemyPacs.add(new Pac(pacId,x,y));
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
                for(int[] dir : DIRECTIONS) {
                    int cx = pac.x;
                    int cy = pac.y;
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
            int[][] boardCopy = copyBoard(board);

            for (Pac p : myPacs) {
                BFSResult result = doBFS(p.x, p.y, boardCopy);

                List<int[]> path = reconstructPath(result, result.bestX, result.bestY, p.x, p.y);

                int stepsToReserve = 5;
                for (int i = 0; i < stepsToReserve && i < path.size(); i++) {
                    int rx = path.get(i)[0];
                    int ry = path.get(i)[1];
                    if (boardCopy[ry][rx] != WALL) {
                        boardCopy[ry][rx] = RESERVED; // Mark as blocked
                    }
                }


                output.append("MOVE ")
                        .append(p.id).append(" ")
                        .append(result.bestX).append(" ")
                        .append(result.bestY).append(" | ");
            }
            printBoard(board, myPacs, enemyPacs);
            System.out.println(output.toString());
        }

    }
    /**
     * Perform a BFS from (startX, startY).
     * We'll keep track of the best-scoring cell as we go.
     */
    static BFSResult doBFS(int startX, int startY, int[][] board) {
        BFSResult res = new BFSResult(width, height);

        // Mark distance/score for the starting cell
        res.distance[startY][startX] = 0;
        res.score[startY][startX] = 0.0;

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startX, startY});

        // We'll track the best cell inline
        double bestScore = -999999.0;
        int bestX = startX;
        int bestY = startY;

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0];
            int cy = current[1];

            int dist = res.distance[cy][cx];
            double curScore = res.score[cy][cx];

            // If this cell's score is better than our known best, update
            if (curScore > bestScore) {
                bestScore = curScore;
                bestX = cx;
                bestY = cy;
            }

            // Explore 4 directions
            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                // Handle horizontal wrapping if needed
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;

                // Typically no vertical wrap, so skip out-of-bounds
                if (ny < 0 || ny >= height) {
                    continue;
                }

                // Skip walls (or any cell you consider "blocked")
                if (board[ny][nx] == WALL) {
                    continue;
                }

                // Check if visited
                if (res.distance[ny][nx] >= 0) {
                    continue; // already discovered
                }

                // Mark as discovered
                res.distance[ny][nx] = dist + 1;
                res.parent[ny][nx] = new int[]{cx, cy};

                // Calculate a path score
                int pelletValue = (board[ny][nx] > 0) ? board[ny][nx] : 0;
                // Example formula: add pelletValue, minus some cost for distance
                // We use (dist) here, not (dist+1), so your exact logic may differ
                double newScore = curScore + (pelletValue * 2) - ((dist + 1) * 0.2);

                res.score[ny][nx] = newScore;

                // Put into queue
                queue.add(new int[]{nx, ny});
            }
        }

        // Save the best cell info in BFSResult so you can reconstruct the path
        res.bestX = bestX;
        res.bestY = bestY;
        res.bestScore = bestScore;

        return res;
    }

    static List<int[]> reconstructPath(BFSResult res, int targetX, int targetY, int startX, int startY) {
        List<int[]> path = new ArrayList<>();
        if (res.distance[targetY][targetX] < 0) {
            // unreachable
            return path;
        }
        int cx = targetX;
        int cy = targetY;

        while (true) {
            path.add(new int[]{cx, cy});
            if (cx == startX && cy == startY) {
                break;
            }
            int[] par = res.parent[cy][cx];
            cx = par[0];
            cy = par[1];
        }
        Collections.reverse(path);
        return path;
    }

    static void printBoard(int[][] board, List<Pac> myPacs, List<Pac> enemyPacs) {
        System.err.println("Board:");

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
                    row.append("🟦"); // Your Pac
                } else if (isEnemyPac) {
                    row.append("🟥"); // Enemy Pac
                } else if (board[i][j] == -1) {
                    row.append("🟫"); // Wall
                } else if (board[i][j] == 10) {
                    row.append("🔴"); // Super Pellet
                } else if (board[i][j] == 1) {
                    row.append("⚪"); // Normal Pellet
                } else {
                    row.append("⬛"); // Empty Floor (no pellet)
                }
            }
            System.err.println(row.toString());
        }
    }

    static class Pac {
        int id, x, y;
        Pac(int id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }
    static class Pellet {
        int x, y, value; // 1 or 10
        Pellet(int x, int y, int v) {
            this.x = x; this.y = y; this.value = v;
        }
    }
    static int[][] copyBoard(int[][] original) {
        int h = original.length;
        int w = original[0].length;
        int[][] copy = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy[y][x] = original[y][x];
            }
        }
        return copy;
    }
    static class BFSResult {
        int[][] distance;    // distance[y][x]
        double[][] score;    // path reward score for (x,y)
        int[][][] parent;    // parent[y][x] = {px, py}
        int bestX, bestY;    // the cell with the best overall score
        double bestScore;
        int width, height;

        BFSResult(int w, int h) {
            this.width = w;
            this.height = h;
            distance = new int[h][w];
            score = new double[h][w];
            parent = new int[h][w][];
            // Initialize distance as -1 => unvisited
            // Initialize score as very negative => no path known
            for (int row = 0; row < h; row++) {
                Arrays.fill(distance[row], -1);
                Arrays.fill(score[row], -999999.0);
            }
            bestX = 0;
            bestY = 0;
            bestScore = -999999.0;
        }
    }


}