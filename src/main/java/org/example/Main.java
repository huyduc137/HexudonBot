package org.example;

import org.example.client.HexudonClient;
import org.example.engine.HexudonEngine;

/**
 * Main — entry point cho chế độ dòng lệnh (CLI), không GUI.
 * Dùng khi chạy bot tự động ngoài môi trường thi đấu thật (không cần thao tác tay).
 * Xem {@code org.example.gui.HexudonGUI} cho chế độ có giao diện điều khiển từng ngày.
 */
public class Main {
    public static void main(String[] args) {
        String url = "https://hexudon.hairbui76.id.vn";
        String gameId = "d2d87157-9158-484f-be37-814a0cf44524";
        String teamId = "21";
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MjEsIm5hbWUiOiJUTEUgU3BlZWRydW4iLCJpc19hZG1pbiI6ZmFsc2UsImlhdCI6MTc4NDI5Nzc3NywiZXhwIjoxNzg0NDcwNTc3fQ.-z4bDE2tFOTll0-yaKsR6ipR-ELnlh7LlnI_nyOH2sY";
        boolean practice = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url": url = args[++i]; break;
                case "--game-id": gameId = args[++i]; break;
                case "--team-id": teamId = args[++i]; break;
                case "--token": token = args[++i]; break;
                case "--practice": practice = true; break;
            }
        }

        if ("YOUR_GAME_ID".equals(gameId)) {
            System.err.println("Vui lòng cung cấp Game ID, Team ID và Token hợp lệ!");
            System.err.println("Cách dùng: java Main --url <URL> --game-id <ID> --team-id <ID> --token <JWT> [--practice]");
            return;
        }

        HexudonClient client = new HexudonClient(url, gameId, teamId, token);
        HexudonEngine engine = new HexudonEngine(client, practice);
        engine.run();
    }
}
