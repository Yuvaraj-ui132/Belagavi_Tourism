# Belagavi Tourism Planner 🗺️

A full-stack, Progressive Web Application (PWA) designed to help users explore, plan, and manage their trips across the historic city of Belagavi, Karnataka.

## 🚀 Features
- **Interactive Explore Map:** Full-screen mapping powered by Leaflet.js, displaying categorized tourist destinations.
- **Secure Admin Dashboard:** A hidden, role-based dashboard for content management (Add, Edit, Delete places).
- **Personalized Wishlist:** Users can save their favorite locations for future trip planning.
- **Budget & Expense Tracker:** Integrated financial tracker to manage travel expenses by location and category.
- **Progressive Web App (PWA):** Installable on mobile and desktop devices with offline caching via Service Workers.
- **Modern UI/UX:** Built with Bootstrap 5, featuring responsive design, glassmorphism elements, and smooth micro-animations.

## 🛠️ Technology Stack
- **Backend:** Python, Flask, Flask-SQLAlchemy, Flask-Login, Werkzeug
- **Database:** SQLite
- **Frontend:** HTML5, Vanilla CSS, JavaScript, Bootstrap 5
- **Mapping:** Leaflet.js, Leaflet MarkerCluster

## 💻 How to Run Locally

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/belagavi-tourism-planner.git
   cd belagavi-tourism-planner
   ```

2. **Create and activate a virtual environment:**
   ```bash
   python -m venv venv
   # On Windows:
   venv\Scripts\activate
   # On Mac/Linux:
   source venv/bin/activate
   ```

3. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Run the application:**
   ```bash
   python app.py
   ```
   *The database (`belagavi.db`) will automatically initialize on the first run.*

5. **Access the application:**
   Open your browser and navigate to `http://127.0.0.1:5000`

## 🔐 Admin Access
To access the Admin Dashboard for content management:
1. Register a new account with the designated admin username.
2. Navigate to the top-right menu and click on the "Admin" button.
