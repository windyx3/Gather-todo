import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', workers: 1, timeout: 60000, globalTimeout: 180000, retries: process.env.CI ? 1 : 0,
  use: { baseURL: 'http://localhost:15173', trace: 'retain-on-failure',
    launchOptions: process.env.PLAYWRIGHT_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH } : {} },
  webServer: [
    { command: 'java -jar ../backend/target/todo.jar --spring.profiles.active=local --server.port=18080 "--spring.datasource.url=jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"', url: 'http://localhost:18080/actuator/health', timeout: 120000, reuseExistingServer: false },
    { command: 'npm run dev -- --port 15173 --strictPort', url: 'http://localhost:15173', timeout: 30000,
      env: { API_TARGET: 'http://localhost:18080' }, reuseExistingServer: false }
  ]
});
