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
        HashMap<String, HashMap<String, Integer>> preCalcDistance = new HashMap<>();


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


            for (Pac p : myPacs) {
                //check if can use ability
                if(p.abilityCooldown == 0) {
                    //next check if enemy pac is in the same cell
                    String enemyInCell = checkIfEnemiesNearby(p, enemyPacs);
                    if(!enemyInCell.equals(""))  {
                        //counter
                        if(enemyInCell.equals("ROCK") && !p.type.equals("PAPER")) {
                            output.append("SWITCH ")
                                    .append(p.id).append(" ")
                                    .append("PAPER").append(" | ");
                            continue;
                        } else if (enemyInCell.equals("PAPER") && !p.type.equals("SCISSORS")) {
                            output.append("SWITCH ")
                                    .append(p.id).append(" ")
                                    .append("SCISSORS").append(" | ");
                            continue;
                        } else if (enemyInCell.equals("SCISSORS") && !p.type.equals("ROCK")) {
                            output.append("SWITCH ")
                                    .append(p.id).append(" ")
                                    .append("ROCK").append(" | ");
                            continue;
                        }

                    }
                }
                int[] move = bfsBestMove(p);
                String moveKey = move[0] + "," + move[1];

                // Mark this cell as occupied

                output.append("MOVE ")
                        .append(p.id).append(" ")
                        .append(move[0]).append(" ")
                        .append(move[1]).append(" | ");
            }
            printBoard(board, myPacs, enemyPacs);
            System.out.println(output.toString());
        }

    }
    static int[] bfsBestMove(Pac pac) {
        Queue<int[]> queue = new LinkedList<>();
        Map<String, Integer> distance = new HashMap<>();
        Map<String, Double> totalScore = new HashMap<>();
        Map<String, int[]> parent = new HashMap<>();

        queue.add(new int[]{pac.x, pac.y});
        distance.put(pac.x + "," + pac.y, 0);
        totalScore.put(pac.x + "," + pac.y, 0.0);

        int[] bestMove = {pac.x, pac.y};
        double bestTotalScore = -1.0;
        int depth = 0;
        while(!queue.isEmpty() || depth == 5) {
            int[] current = queue.poll();
            int cx = current[0];
            int cy = current[1];
            int currDist = distance.get(cx + "," + cy);

            for(int[] dir: DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];

                //handle wrapping
                if (nx < 0) nx = width - 1;
                else if (nx >= width) nx = 0;
                if (ny < 0 || ny >= height) continue; // No vertical wrapping

                if (board[ny][nx] == WALL) continue;

                // Already visited?
                String key = nx + "," + ny;
                if (distance.containsKey(key)) continue;

                distance.put(key, currDist + 1);
                queue.add(new int[]{nx, ny});
                parent.put(key, new int[]{cx, cy});

                // Reward calculation
                int pelletValue = board[ny][nx]; // 0 (empty), 1 (pellet), 10 (super pellet)
                double rawScore = totalScore.get(cx + "," + cy)
                        + (pelletValue * 3)
                        - (currDist * 0.1);
                double pathScore = Math.max(rawScore, 0);
                totalScore.put(key, pathScore);

                // If this path leads to a better total reward, update bestMove
                if (pathScore > bestTotalScore) {
                    bestTotalScore = pathScore;
                    bestMove = new int[]{nx, ny};
                }

            }
            depth = depth + 1;

        }
        return bestMove;
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

}
/**
 * Limit the BFS depth
 * commit to a path, unless other path difference is significant
 * use speed boost
 */