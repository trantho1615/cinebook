export default {
    testDir: "web-test/e2e",
    timeout: 60_000,
    use: { baseURL: "http://localhost:8080", trace: "retain-on-failure" },
};
