// ===== SURVIVAL FIRE - 2D Battle Royale =====

const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
const W = canvas.width;
const H = canvas.height;

// ---- Game State ----
let game = {
  running: false,
  player: null,
  enemies: [],
  bullets: [],
  particles: [],
  lootBoxes: [],
  obstacles: [],
  zone: null,
  kills: 0,
  startTime: 0,
};

const keys = {};
let mouse = { x: W/2, y: H/2, down: false };

// ---- Input ----
window.addEventListener('keydown', e => { keys[e.key.toLowerCase()] = true; if(e.key === ' ') e.preventDefault(); });
window.addEventListener('keyup', e => { keys[e.key.toLowerCase()] = false; });
canvas.addEventListener('mousemove', e => {
  const r = canvas.getBoundingClientRect();
  mouse.x = (e.clientX - r.left) * (W / r.width);
  mouse.y = (e.clientY - r.top) * (H / r.height);
});
canvas.addEventListener('mousedown', () => { mouse.down = true; });
canvas.addEventListener('mouseup', () => { mouse.down = false; });

// ---- Utility ----
const rand = (min, max) => Math.random() * (max - min) + min;
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
const angleBetween = (a, b) => Math.atan2(b.y - a.y, b.x - a.x);

// ---- Entities ----
class Player {
  constructor(x, y, isBot = false) {
    this.x = x; this.y = y;
    this.radius = 16;
    this.speed = 2.5;
    this.health = 100;
    this.maxHealth = 100;
    this.angle = 0;
    this.isBot = isBot;
    this.alive = true;
    this.color = isBot ? `hsl(${rand(0,360)},60%,50%)` : '#4fc3f7';
    this.name = isBot ? `Bot${Math.floor(rand(1,999))}` : 'YOU';
    this.shootCooldown = 0;
    this.shootRate = isBot ? 30 : 15;
    this.bulletSpeed = 7;
    this.damage = isBot ? 8 : 15;
    this.kills = 0;
    this.target = null;
    this.wanderTimer = 0;
    this.wanderAngle = rand(0, Math.PI*2);
  }

  update() {
    if (!this.alive) return;

    if (this.isBot) {
      this.botAI();
    } else {
      this.playerControl();
    }

    // Aim
    this.angle = Math.atan2(mouse.y - this.y, mouse.x - this.x);

    // Shoot
    if (this.shootCooldown > 0) this.shootCooldown--;
    if ((this.isBot || mouse.down || keys[' ']) && this.shootCooldown <= 0) {
      this.shoot();
      this.shootCooldown = this.shootRate;
    }
  }

  playerControl() {
    let dx = 0, dy = 0;
    if (keys['w'] || keys['arrowup']) dy -= 1;
    if (keys['s'] || keys['arrowdown']) dy += 1;
    if (keys['a'] || keys['arrowleft']) dx -= 1;
    if (keys['d'] || keys['arrowright']) dx += 1;
    const len = Math.hypot(dx, dy);
    if (len > 0) { dx /= len; dy /= len; }
    let spd = this.speed;
    if (keys['shift'] && (dx || dy)) spd *= 1.6;
    this.x += dx * spd;
    this.y += dy * spd;
    this.x = Math.max(this.radius, Math.min(W - this.radius, this.x));
    this.y = Math.max(this.radius, Math.min(H - this.radius, this.y));
  }

  botAI() {
    // Find nearest alive target
    let nearest = null, minD = Infinity;
    const all = [game.player, ...game.enemies].filter(e => e && e.alive && e !== this);
    for (const e of all) {
      const d = dist(this, e);
      if (d < minD) { minD = d; nearest = e; }
    }
    this.target = nearest;

    let dx = 0, dy = 0;
    if (nearest && minD < 400) {
      // Approach to medium range
      if (minD > 200) {
        dx = Math.cos(angleBetween(this, nearest));
        dy = Math.sin(angleBetween(this, nearest));
      } else if (minD < 100) {
        // Back away
        dx = -Math.cos(angleBetween(this, nearest));
        dy = -Math.sin(angleBetween(this, nearest));
      }
      // Aim at target
      this.angle = angleBetween(this, nearest);
    } else {
      // Wander
      this.wanderTimer--;
      if (this.wanderTimer <= 0) {
        this.wanderAngle = rand(0, Math.PI*2);
        this.wanderTimer = Math.floor(rand(60, 180));
      }
      dx = Math.cos(this.wanderAngle);
      dy = Math.sin(this.wanderAngle);
      this.angle = this.wanderAngle;
    }

    // Move toward zone center if outside zone
    if (game.zone && !game.zone.isInside(this)) {
      const zAng = angleBetween(this, { x: game.zone.x, y: game.zone.y });
      dx = Math.cos(zAng);
      dy = Math.sin(zAng);
    }

    const len = Math.hypot(dx, dy);
    if (len > 0) { dx /= len; dy /= len; }
    this.x += dx * this.speed;
    this.y += dy * this.speed;
    this.x = Math.max(this.radius, Math.min(W - this.radius, this.x));
    this.y = Math.max(this.radius, Math.min(H - this.radius, this.y));

    // Bots shoot if target is close and roughly in front
    if (nearest && minD < 350) {
      mouse.down = false; // ensure bots use their own shoot logic
      if ((this.isBot) && this.shootCooldown <= 0) {
        this.shoot();
        this.shootCooldown = this.shootRate + Math.floor(rand(0, 20));
      }
    }
  }

  shoot() {
    const bx = this.x + Math.cos(this.angle) * (this.radius + 4);
    const by = this.y + Math.sin(this.angle) * (this.radius + 4);
    game.bullets.push(new Bullet(bx, by, this.angle, this.bulletSpeed, this.damage, this));
    // Muzzle flash particles
    for (let i = 0; i < 3; i++) {
      game.particles.push(new Particle(bx, by, this.angle + rand(-0.3, 0.3), rand(2, 5), '#ffaa44', 8));
    }
  }

  takeDamage(amount, attacker) {
    this.health -= amount;
    // Blood particles
    for (let i = 0; i < 6; i++) {
      game.particles.push(new Particle(this.x, this.y, rand(0, Math.PI*2), rand(1, 4), '#cc2222', 15));
    }
    if (this.health <= 0) {
      this.alive = false;
      this.health = 0;
      // Death explosion
      for (let i = 0; i < 25; i++) {
        game.particles.push(new Particle(this.x, this.y, rand(0, Math.PI*2), rand(2, 7), this.color, 30));
      }
      if (attacker) attacker.kills++;
      if (!this.isBot) {
        endGame(false);
      }
    }
  }

  draw() {
    if (!this.alive) return;
    ctx.save();

    // Body
    ctx.fillStyle = this.color;
    ctx.shadowColor = this.color;
    ctx.shadowBlur = 8;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI*2);
    ctx.fill();
    ctx.shadowBlur = 0;

    // Gun
    ctx.strokeStyle = '#555';
    ctx.lineWidth = 5;
    ctx.beginPath();
    ctx.moveTo(this.x, this.y);
    ctx.lineTo(this.x + Math.cos(this.angle) * 24, this.y + Math.sin(this.angle) * 24);
    ctx.stroke();

    // Gun tip
    ctx.fillStyle = '#888';
    ctx.beginPath();
    ctx.arc(this.x + Math.cos(this.angle) * 24, this.y + Math.sin(this.angle) * 24, 3, 0, Math.PI*2);
    ctx.fill();

    // Name
    ctx.fillStyle = this.isBot ? '#ccc' : '#4fc3f7';
    ctx.font = 'bold 11px Tahoma';
    ctx.textAlign = 'center';
    ctx.fillText(this.name, this.x, this.y - this.radius - 6);

    // Health bar
    const barW = 36, barH = 4;
    ctx.fillStyle = 'rgba(0,0,0,0.5)';
    ctx.fillRect(this.x - barW/2, this.y - this.radius - 16, barW, barH);
    ctx.fillStyle = this.health > 50 ? '#44ff44' : (this.health > 25 ? '#ffaa00' : '#ff4444');
    ctx.fillRect(this.x - barW/2, this.y - this.radius - 16, barW * (this.health / this.maxHealth), barH);

    ctx.restore();
  }
}

class Bullet {
  constructor(x, y, angle, speed, damage, owner) {
    this.x = x; this.y = y;
    this.vx = Math.cos(angle) * speed;
    this.vy = Math.sin(angle) * speed;
    this.damage = damage;
    this.owner = owner;
    this.radius = 3;
    this.life = 120;
    this.trail = [];
  }

  update() {
    this.trail.push({ x: this.x, y: this.y });
    if (this.trail.length > 6) this.trail.shift();
    this.x += this.vx;
    this.y += this.vy;
    this.life--;

    // Wall collision
    if (this.x < 0 || this.x > W || this.y < 0 || this.y > H) return false;
    if (this.life <= 0) return false;

    // Hit detection
    const all = [game.player, ...game.enemies].filter(e => e && e.alive && e !== this.owner);
    for (const e of all) {
      if (dist(this, e) < e.radius + this.radius) {
        e.takeDamage(this.damage, this.owner);
        return false;
      }
    }
    return true;
  }

  draw() {
    // Trail
    for (let i = 0; i < this.trail.length; i++) {
      const a = i / this.trail.length;
      ctx.fillStyle = `rgba(255,220,100,${a * 0.5})`;
      ctx.beginPath();
      ctx.arc(this.trail[i].x, this.trail[i].y, this.radius * a, 0, Math.PI*2);
      ctx.fill();
    }
    // Bullet
    ctx.fillStyle = '#ffeb3b';
    ctx.shadowColor = '#ffeb3b';
    ctx.shadowBlur = 6;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI*2);
    ctx.fill();
    ctx.shadowBlur = 0;
  }
}

class Particle {
  constructor(x, y, angle, speed, color, life) {
    this.x = x; this.y = y;
    this.vx = Math.cos(angle) * speed;
    this.vy = Math.sin(angle) * speed;
    this.color = color;
    this.life = life;
    this.maxLife = life;
    this.radius = rand(1, 3);
  }
  update() {
    this.x += this.vx;
    this.y += this.vy;
    this.vx *= 0.93;
    this.vy *= 0.93;
    this.life--;
    return this.life > 0;
  }
  draw() {
    const a = this.life / this.maxLife;
    ctx.fillStyle = this.color;
    ctx.globalAlpha = a;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI*2);
    ctx.fill();
    ctx.globalAlpha = 1;
  }
}

class LootBox {
  constructor(x, y) {
    this.x = x; this.y = y;
    this.radius = 12;
    this.collected = false;
    this.pulse = 0;
  }
  update() {
    this.pulse += 0.05;
    if (!this.collected && game.player.alive && dist(this, game.player) < this.radius + game.player.radius) {
      this.collected = true;
      game.player.health = Math.min(game.player.maxHealth, game.player.health + 25);
      game.player.damage += 5;
      game.player.bulletSpeed += 0.5;
      game.player.shootRate = Math.max(6, game.player.shootRate - 1);
      // Sparkle
      for (let i = 0; i < 15; i++) {
        game.particles.push(new Particle(this.x, this.y, rand(0, Math.PI*2), rand(1, 5), '#44ff88', 20));
      }
    }
  }
  draw() {
    if (this.collected) return;
    const p = Math.sin(this.pulse) * 0.3 + 0.7;
    ctx.save();
    ctx.translate(this.x, this.y);
    ctx.rotate(this.pulse * 0.3);
    ctx.fillStyle = `rgba(68,255,136,${p})`;
    ctx.shadowColor = '#44ff88';
    ctx.shadowBlur = 12;
    ctx.fillRect(-8, -8, 16, 16);
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 2;
    ctx.strokeRect(-8, -8, 16, 16);
    ctx.restore();
    ctx.shadowBlur = 0;
  }
}

class SafeZone {
  constructor() {
    this.x = W / 2;
    this.y = H / 2;
    this.radius = Math.min(W, H) / 2 - 20;
    this.targetRadius = this.radius;
    this.shrinkSpeed = 0.4;
    this.timer = 30 * 60; // 30 seconds at 60fps
    this.phase = 0;
    this.damage = 0.3;
  }

  update() {
    this.timer--;
    document.getElementById('zoneTimer').textContent = Math.max(0, Math.ceil(this.timer / 60));

    if (this.timer <= 0 && this.radius > 80) {
      this.targetRadius = this.radius * 0.65;
      this.timer = 25 * 60;
      this.phase++;
      this.damage += 0.15;
    }

    if (this.radius > this.targetRadius) {
      this.radius -= this.shrinkSpeed;
    }

    // Damage players outside zone
    const all = [game.player, ...game.enemies].filter(e => e && e.alive);
    for (const e of all) {
      if (!this.isInside(e)) {
        e.health -= this.damage;
        if (e.health <= 0 && e.alive) {
          e.alive = false;
          e.health = 0;
          for (let i = 0; i < 15; i++) {
            game.particles.push(new Particle(e.x, e.y, rand(0, Math.PI*2), rand(1, 5), e.color, 25));
          }
          if (!e.isBot) endGame(false);
        }
      }
    }
  }

  isInside(entity) {
    return dist(entity, { x: this.x, y: this.y }) < this.radius;
  }

  draw() {
    // Zone circle (red danger outside)
    ctx.save();
    ctx.strokeStyle = 'rgba(255,50,50,0.8)';
    ctx.lineWidth = 3;
    ctx.setLineDash([10, 6]);
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI*2);
    ctx.stroke();
    ctx.setLineDash([]);

    // Shade outside zone
    ctx.fillStyle = 'rgba(255,0,0,0.06)';
    ctx.beginPath();
    ctx.rect(0, 0, W, H);
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI*2, true);
    ctx.fill();
    ctx.restore();
  }
}

// ---- Game Setup ----
function initGame() {
  game = {
    running: true,
    player: new Player(W/2, H/2, false),
    enemies: [],
    bullets: [],
    particles: [],
    lootBoxes: [],
    obstacles: [],
    zone: new SafeZone(),
    kills: 0,
    startTime: Date.now(),
  };

  // Spawn enemies (9 bots for 10 total players)
  for (let i = 0; i < 9; i++) {
    const angle = (i / 9) * Math.PI * 2;
    const r = rand(150, 300);
    game.enemies.push(new Player(W/2 + Math.cos(angle)*r, H/2 + Math.sin(angle)*r, true));
  }

  // Spawn loot boxes
  for (let i = 0; i < 6; i++) {
    game.lootBoxes.push(new LootBox(rand(50, W-50), rand(50, H-50)));
  }

  document.getElementById('overlay').style.display = 'none';
  gameLoop();
}

function getAliveCount() {
  let c = game.player.alive ? 1 : 0;
  for (const e of game.enemies) if (e.alive) c++;
  return c;
}

// ---- Game Loop ----
function gameLoop() {
  if (!game.running) return;

  // Clear
  ctx.fillStyle = '#1a2a1a';
  ctx.fillRect(0, 0, W, H);

  // Grid pattern
  ctx.strokeStyle = 'rgba(255,255,255,0.03)';
  ctx.lineWidth = 1;
  for (let x = 0; x < W; x += 40) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
  }
  for (let y = 0; y < H; y += 40) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
  }

  // Update zone
  game.zone.update();
  game.zone.draw();

  // Update loot boxes
  for (const lb of game.lootBoxes) { lb.update(); lb.draw(); }

  // Update bullets
  game.bullets = game.bullets.filter(b => {
    const alive = b.update();
    if (alive) b.draw();
    return alive;
  });

  // Update player
  game.player.update();
  game.player.draw();

  // Update enemies
  for (const e of game.enemies) {
    e.update();
    e.draw();
  }

  // Update particles
  game.particles = game.particles.filter(p => {
    const alive = p.update();
    if (alive) p.draw();
    return alive;
  });

  // Update HUD
  document.getElementById('healthFill').style.width = (game.player.health / game.player.maxHealth * 100) + '%';
  document.getElementById('killCount').textContent = game.player.kills;
  const alive = getAliveCount();
  document.getElementById('aliveCount').textContent = alive;

  // Check win condition
  if (alive === 1 && game.player.alive) {
    endGame(true);
  }

  requestAnimationFrame(gameLoop);
}

// ---- End Game ----
function endGame(victory) {
  game.running = false;
  const overlay = document.getElementById('overlay');
  const timeSurvived = Math.floor((Date.now() - game.startTime) / 1000);
  const mins = Math.floor(timeSurvived / 60);
  const secs = timeSurvived % 60;

  overlay.innerHTML = `
    <h1>🔥 SURVIVAL FIRE 🔥</h1>
    <div id="gameResult" class="${victory ? 'win' : 'lose'}">${victory ? '🏆 BOOYAH! You Won!' : '💀 You Died'}</div>
    <div id="finalStats">
      ${victory ? 'You are the last one standing!' : 'Better luck next time, fighter.'}<br>
      Kills: ${game.player.kills} &nbsp;|&nbsp; Time: ${mins}:${secs.toString().padStart(2,'0')}
    </div>
    <div class="controls">
      <b>Move</b>: <span>W A S D</span> &nbsp;|&nbsp;
      <b>Aim</b>: <span>Mouse</span> &nbsp;|&nbsp;
      <b>Shoot</b>: <span>Click / Spacebar</span> &nbsp;|&nbsp;
      <b>Sprint</b>: <span>Shift</span>
    </div>
    <button id="restartBtn">PLAY AGAIN</button>
  `;
  overlay.style.display = 'flex';
  document.getElementById('restartBtn').addEventListener('click', initGame);
}

// ---- Start ----
document.getElementById('startBtn').addEventListener('click', initGame);
