I’ll check the project’s Git status and `.gitignore` so the steps match your setup.

Your project isn’t a Git repository yet, and your [`.gitignore`](/home/gnostex/Documents/nakorn/application/nakorn-thai/.gitignore) is empty.

1. **Add this to `.gitignore`** for your Java backend and frontend:

   ```gitignore
   # Dependencies and build output
   node_modules/
   target/
   dist/

   # Local environment files
   .env
   .env.*
   !.env.example

   # IDE and operating system files
   .idea/
   *.iml
   .DS_Store
   Thumbs.db

   # Logs
   *.log
   ```

2. **Create an empty repository** at [github.com/new](https://github.com/new):
   - Name it `nakorn-thai`.
   - Choose **Private** or **Public**.
   - Leave README, `.gitignore`, and license initialization unchecked because you’re uploading an existing project. [GitHub instructions](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)

3. **Initialize Git** in your terminal:

   ```bash
   cd /home/gnostex/Documents/nakorn/application/nakorn-thai
   git init -b main
   git add .
   git status
   ```

   Review the staged files before committing. Ensure credentials, `.env` files, and `node_modules` aren’t included.

4. **Create your first commit:**

   ```bash
   git commit -m "Initial commit"
   ```

   If Git asks for your identity, set it and retry:

   ```bash
   git config user.name "Your Name"
   git config user.email "you@example.com"
   ```

5. **Connect and upload**, replacing `YOUR_USERNAME`:

   ```bash
   git remote add origin https://github.com/YOUR_USERNAME/nakorn-thai.git
   git push -u origin main
   ```

   Authenticate when prompted. For HTTPS password prompts, use a GitHub personal access token instead of your account password.

Refresh the GitHub repository page to see your code.