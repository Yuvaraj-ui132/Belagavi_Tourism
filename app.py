import os
from flask import Flask, render_template, redirect, url_for, request, flash, jsonify, make_response
from flask_login import LoginManager, login_user, login_required, logout_user, current_user
from werkzeug.security import generate_password_hash, check_password_hash
from models import db, User, Place, Wishlist, Expense

app = Flask(__name__)
app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY', 'change-me-in-production')

db_url = os.environ.get('DATABASE_URL', 'sqlite:///belagavi.db')
if db_url.startswith("postgres://"):
    db_url = db_url.replace("postgres://", "postgresql://", 1)

app.config['SQLALCHEMY_DATABASE_URI'] = db_url

db.init_app(app)

from data import PLACE_DETAILS
with app.app_context():
    db.create_all()
    if not Place.query.first():
        for folder_name, details in PLACE_DETAILS.items():
            new_place = Place(
                name=details.get('name'),
                folder_name=folder_name,
                category=details.get('category'),
                description=(details.get('history') or '')[:200],
                history=details.get('history'),
                architecture=details.get('architecture'),
                famous_features=details.get('famous_features'),
                lat=details.get('lat'),
                lon=details.get('lon'),
                best_time=details.get('best_time'),
                entry_fee=details.get('entry_fee'),
                visit_duration=details.get('visit_duration'),
                city=details.get('city'),
                how_to_reach=details.get('how_to_reach'),
                local_tips=details.get('local_tips'),
                detailed_history=details.get('detailed_history')
            )
            db.session.add(new_place)
            
    if not User.query.filter_by(username='Yuvaraj').first():
        hashed_pw = generate_password_hash('Yuvaraj1718', method='scrypt')
        admin_user = User(username='Yuvaraj', email='yuvaraj@gmail.com', password=hashed_pw)
        db.session.add(admin_user)
        
    db.session.commit()

login_manager = LoginManager(app)
login_manager.login_view = 'login'


def _safe_internal_path(path):
    """Allow only same-site relative redirects."""
    return bool(path) and isinstance(path, str) and path.startswith('/') and not path.startswith('//')


@login_manager.user_loader
def load_user(user_id):
    return User.query.get(int(user_id))


@app.route('/')
def welcome():
    if current_user.is_authenticated:
        return redirect(url_for('home'))
    return render_template('welcome.html')


@app.route('/planner')
@login_required
def home():
    featured_names = ['jalavane_falls', 'kamalbasadi_belagavi', 'vidhansoudha_belagavi', 'chorla_ghat', 'yellur_fort', 'vajrapoha_falls']
    places = Place.query.filter(Place.folder_name.in_(featured_names)).all()
    all_places = Place.query.all()
    user_wishlist = Wishlist.query.filter_by(user_id=current_user.id).all()
    return render_template('home.html', places=places, all_places_list=all_places, user_wishlist=user_wishlist)


@app.route('/login', methods=['GET', 'POST'])
def login():
    if current_user.is_authenticated:
        return redirect(url_for('home'))
    if request.method == 'POST':
        username = request.form.get('username', '').strip()
        password = request.form.get('password', '')
        user = User.query.filter_by(username=username).first()
        if user and check_password_hash(user.password, password):
            login_user(user)
            next_url = request.args.get('next')
            if _safe_internal_path(next_url):
                return redirect(next_url)
            return redirect(url_for('home'))
        flash('Invalid username or password')
    return render_template('login.html')


@app.route('/register', methods=['GET', 'POST'])
def register():
    if current_user.is_authenticated:
        return redirect(url_for('home'))
    if request.method == 'POST':
        username = request.form.get('username', '').strip()
        email = request.form.get('email', '').strip()
        password = request.form.get('password', '')
        if not username or not email or not password:
            flash('All fields are required.')
            return redirect(url_for('register'))

        if User.query.filter_by(username=username).first():
            flash('Username is already taken.')
            return redirect(url_for('register'))

        if User.query.filter_by(email=email).first():
            flash('Email is already registered. Please log in instead.')
            return redirect(url_for('login'))

        # Upgraded to scrypt
        hashed_pw = generate_password_hash(password, method='scrypt')
        new_user = User(username=username, email=email, password=hashed_pw)
        db.session.add(new_user)
        db.session.commit()
        return redirect(url_for('login'))
    return render_template('register.html')


@app.route('/place/<int:place_id>')
@login_required
def place_details(place_id):
    place = Place.query.get_or_404(place_id)

    image_folder = os.path.join(app.root_path, 'static', 'images', place.folder_name)
    images = []
    if os.path.exists(image_folder):
        images = sorted(
            (f for f in os.listdir(image_folder) if f.lower().endswith(('.jpg', '.png', '.jpeg')))
        )

    recommended_places = (
        Place.query.filter(
            Place.id != place.id,
            Place.category == place.category,
        )
        .order_by(Place.name)
        .limit(4)
        .all()
    )

    return render_template(
        'details.html',
        place=place,
        images=images,
        recommended_places=recommended_places,
    )

@app.route('/navigate/<int:place_id>')
@login_required
def navigate(place_id):
    place = Place.query.get_or_404(place_id)
    return render_template('navigate.html', place=place)



@app.route('/sw.js')
def sw():
    response = make_response(app.send_static_file('sw.js'))
    response.headers['Content-Type'] = 'application/javascript'
    return response

@app.route('/admin')
@login_required
def admin_dashboard():
    if current_user.username.lower() != 'yuvaraj':
        flash('Access Denied. Admins only.')
        return redirect(url_for('home'))
    all_places = Place.query.all()
    return render_template('admin.html', places=all_places)

@app.route('/admin/edit/<int:place_id>', methods=['POST'])
@login_required
def admin_edit(place_id):
    if current_user.username.lower() != 'yuvaraj':
        return redirect(url_for('home'))
    place = Place.query.get_or_404(place_id)
    place.name = request.form.get('name', place.name)
    place.category = request.form.get('category', place.category)
    place.description = request.form.get('description', place.description)
    db.session.commit()
    flash(f'{place.name} updated successfully!')
    return redirect(url_for('admin_dashboard'))

@app.route('/admin/add', methods=['POST'])
@login_required
def admin_add():
    if current_user.username.lower() != 'yuvaraj':
        return redirect(url_for('home'))
    new_place = Place(
        name=request.form.get('name', 'New Place'),
        folder_name=request.form.get('folder_name', 'default'),
        category=request.form.get('category', 'Local Discovery'),
        description=request.form.get('description', '')
    )
    db.session.add(new_place)
    db.session.commit()
    flash(f'{new_place.name} added successfully!')
    return redirect(url_for('admin_dashboard'))

@app.route('/admin/delete/<int:place_id>', methods=['POST'])
@login_required
def admin_delete(place_id):
    if current_user.username.lower() != 'yuvaraj':
        return redirect(url_for('home'))
    place = Place.query.get_or_404(place_id)
    Wishlist.query.filter_by(place_id=place.id).delete()
    db.session.delete(place)
    db.session.commit()
    flash(f'{place.name} deleted successfully!')
    return redirect(url_for('admin_dashboard'))

# --- USER DATA APIS ---

@app.route('/add_to_wishlist/<int:place_id>', methods=['POST'])
@login_required
def add_to_wishlist(place_id):
    exists = Wishlist.query.filter_by(user_id=current_user.id, place_id=place_id).first()
    if not exists:
        db.session.add(Wishlist(user_id=current_user.id, place_id=place_id))
        db.session.commit()
    return jsonify({"status": "success"})


@app.route('/remove_from_wishlist/<int:place_id>', methods=['POST'])
@login_required
def remove_from_wishlist(place_id):
    item = Wishlist.query.filter_by(user_id=current_user.id, place_id=place_id).first()
    if item:
        db.session.delete(item)
        db.session.commit()
    return jsonify({"status": "success"})


@app.route('/api/expenses', methods=['GET', 'POST'])
@login_required
def manage_expenses():
    if request.method == 'GET':
        expenses = Expense.query.filter_by(user_id=current_user.id).order_by(Expense.id.desc()).all()
        return jsonify([{
            'id': e.id,
            'location': e.location,
            'name': e.name,
            'amount': e.amount,
            'category': e.category,
            'date': e.date
        } for e in expenses])
    
    if request.method == 'POST':
        data = request.json
        new_exp = Expense(
            user_id=current_user.id,
            location=data.get('location'),
            name=data.get('name'),
            amount=data.get('amount'),
            category=data.get('category'),
            date=data.get('date')
        )
        db.session.add(new_exp)
        db.session.commit()
        return jsonify({'status': 'success', 'id': new_exp.id})

@app.route('/api/expenses/<int:expense_id>', methods=['DELETE'])
@login_required
def delete_expense(expense_id):
    expense = Expense.query.filter_by(id=expense_id, user_id=current_user.id).first()
    if expense:
        db.session.delete(expense)
        db.session.commit()
        return jsonify({'status': 'success'})
    return jsonify({'error': 'Not found'}), 404

@app.route('/api/expenses/clear', methods=['POST'])
@login_required
def clear_expenses():
    Expense.query.filter_by(user_id=current_user.id).delete()
    db.session.commit()
    return jsonify({'status': 'success'})




@app.route('/logout')
@login_required
def logout():
    logout_user()
    return redirect(url_for('welcome'))


if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True)