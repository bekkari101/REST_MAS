from flask import Flask, request, jsonify, render_template, send_from_directory
import os
import time

app = Flask(__name__, static_folder='../static', template_folder='../static')

# In-memory storage for agent states
agents_data = {}

# Debug messages storage (circular buffer)
debug_messages = []
MAX_DEBUG_MESSAGES = 500

# Simulation control state
sim_state = {
    "running": False,
    "speed": 1.0,
    "initialized": False,
    "grid_width": 120,
    "grid_height": 120
}

@app.route('/')
def index():
    """Serve the simulation dashboard."""
    return render_template('index.html')

@app.route('/control', methods=['GET', 'POST'])
def control():
    """Manage simulation state."""
    if request.method == 'POST':
        data = request.json
        if 'running' in data:
            sim_state['running'] = data['running']
        if 'speed' in data:
            sim_state['speed'] = float(data['speed'])
        if 'initialized' in data:
            sim_state['initialized'] = data['initialized']
        if 'grid_width' in data:
            sim_state['grid_width'] = int(data['grid_width'])
        if 'grid_height' in data:
            sim_state['grid_height'] = int(data['grid_height'])
        return jsonify(sim_state)
    return jsonify(sim_state)

@app.route('/update', methods=['POST'])
def update_agent():
    """Update agent state."""
    data = request.json
    if not data or 'id' not in data:
        return jsonify({"status": "error", "message": "Missing agent id"}), 400
    
    agent_id = data['id']
    data['last_seen'] = time.time() # Add timestamp
    agents_data[agent_id] = data
    return jsonify({"status": "success"}), 200

@app.route('/debug', methods=['POST'])
def add_debug_message():
    """Add a debug message from an agent."""
    data = request.json
    if not data or 'agent_id' not in data or 'message' not in data:
        return jsonify({"status": "error", "message": "Missing required fields"}), 400
    
    debug_msg = {
        "timestamp": time.time(),
        "agent_id": data['agent_id'],
        "agent_type": data.get('agent_type', 'unknown'),
        "message": data['message'],
        "level": data.get('level', 'INFO'),  # DEBUG, INFO, WARNING, ERROR
        "container": data.get('container', 'unknown'),
        "status": data.get('status', ''),
        "details": data.get('details', {})
    }
    
    debug_messages.append(debug_msg)
    
    # Keep only last MAX_DEBUG_MESSAGES
    if len(debug_messages) > MAX_DEBUG_MESSAGES:
        debug_messages.pop(0)
    
    return jsonify({"status": "success"}), 200

@app.route('/debug', methods=['GET'])
def get_debug_messages():
    """Get all debug messages (optionally filtered)."""
    agent_filter = request.args.get('agent_id')
    level_filter = request.args.get('level')
    limit = int(request.args.get('limit', 100))
    
    filtered_messages = debug_messages
    
    if agent_filter:
        filtered_messages = [m for m in filtered_messages if m['agent_id'] == agent_filter]
    
    if level_filter:
        filtered_messages = [m for m in filtered_messages if m['level'] == level_filter]
    
    # Return most recent messages
    return jsonify(filtered_messages[-limit:])

@app.route('/debug/clear', methods=['POST'])
def clear_debug_messages():
    """Clear all debug messages."""
    debug_messages.clear()
    return jsonify({"status": "success"}), 200

@app.route('/remove/<agent_id>', methods=['POST', 'DELETE'])
def remove_agent(agent_id):
    """Explicitly remove an agent."""
    if agent_id in agents_data:
        del agents_data[agent_id]
        return jsonify({"status": "success"}), 200
    return jsonify({"status": "not_found"}), 404

@app.route('/clear', methods=['POST'])
def clear_agents():
    """Clear all agent data."""
    agents_data.clear()
    return jsonify({"status": "success"}), 200

@app.route('/agents', methods=['GET'])
def get_agents():
    """Return all current agent states (cleaning up stale ones)."""
    current_time = time.time()
    stale_ids = [aid for aid, data in agents_data.items() 
                 if current_time - data.get('last_seen', 0) > 10] # 10 seconds timeout
    for aid in stale_ids:
        del agents_data[aid]
        
    return jsonify(list(agents_data.values()))

# Counter for generating unique client names
client_counter = 1

@app.route('/add_client', methods=['POST'])
def add_client():
    """Request creation of a new client agent."""
    global client_counter
    
    try:
        # Generate unique client name
        client_name = f"Client{client_counter}"
        client_counter += 1
        
        # Write client creation request to a file that Java can poll
        request_file = os.path.join(os.path.dirname(__file__), '..', 'client_requests.txt')
        with open(request_file, 'a') as f:
            f.write(f"{client_name}\n")
        
        return jsonify({
            "status": "success", 
            "client_name": client_name,
            "message": f"Client creation requested: {client_name}"
        }), 200
    except Exception as e:
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500

@app.route('/static/<path:path>')
def send_static(path):
    return send_from_directory('../static', path)

if __name__ == '__main__':
    # Get port from environment or use default 5001
    port = int(os.environ.get('PORT', 5001))
    print(f" * Starting Neon Bistro Sim API on port {port}")
    print(f" * Serving static files from: {os.path.abspath('../static')}")
    app.run(host='0.0.0.0', port=port, debug=True)
