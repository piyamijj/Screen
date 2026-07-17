import * as THREE from 'three';

// 1. SAHNE, SİS VE KAMERA
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x001a33); // Daha derin bir lacivert
scene.fog = new THREE.FogExp2(0x001a33, 0.015);

const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 1000);
camera.position.set(0, 0, 40);

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setPixelRatio(window.devicePixelRatio);
document.body.appendChild(renderer.domElement);

// 2. IŞIKLANDIRMA
const ambientLight = new THREE.AmbientLight(0x406080, 2.5); 
scene.add(ambientLight);
const directionalLight = new THREE.DirectionalLight(0xffddaa, 2); 
directionalLight.position.set(0, 50, 20);
scene.add(directionalLight);

// 3. SU KABARCIKLARI VE PLANKTONLAR
const particleGeo = new THREE.BufferGeometry();
const particleCount = 1000;
const posArray = new Float32Array(particleCount * 3);
for(let i = 0; i < particleCount * 3; i++) {
    posArray[i] = (Math.random() - 0.5) * 120;
}
particleGeo.setAttribute('position', new THREE.BufferAttribute(posArray, 3));
const particleMat = new THREE.PointsMaterial({ size: 0.1, color: 0xffffff, transparent: true, opacity: 0.3 });
const particlesMesh = new THREE.Points(particleGeo, particleMat);
scene.add(particlesMesh);

// 4. BEŞ FARKLI BALIK TÜRÜ OLUŞTURUCU (Yapay 3D Modelleme)
const fishes = [];
const foods = []; // Yemleri tutacağımız dizi

// Türlerin Özellikleri
const speciesData = [
    { name: 'Palyaço', color: 0xff5500, tailColor: 0xffffff, speed: 0.08, bodyScale: [0.6, 1.5, 0.4] },
    { name: 'Mavi Tang', color: 0x0044ff, tailColor: 0xffcc00, speed: 0.1, bodyScale: [0.4, 1.8, 0.8] }, // Dory
    { name: 'Melek', color: 0xdddddd, tailColor: 0x222222, speed: 0.06, bodyScale: [0.2, 1.5, 1.5] }, // İnce ve yüksek
    { name: 'Neon Tetra', color: 0x00ffff, tailColor: 0xff0055, speed: 0.12, bodyScale: [0.3, 1.2, 0.3] }, // Küçük, hızlı
    { name: 'Japon', color: 0xff8800, tailColor: 0xff4400, speed: 0.05, bodyScale: [0.8, 1.6, 0.6] } // Tombul
];

const baseBodyGeo = new THREE.SphereGeometry(1, 16, 16); 
const baseTailGeo = new THREE.ConeGeometry(0.5, 1.5, 3);
baseTailGeo.rotateX(-Math.PI / 2);
baseTailGeo.translate(0, 0, -1.2);

for(let i = 0; i < 40; i++) {
    // Rastgele bir tür seç
    const sp = speciesData[Math.floor(Math.random() * speciesData.length)];
    
    const bodyMat = new THREE.MeshStandardMaterial({ color: sp.color, roughness: 0.3, metalness: 0.5 });
    const tailMat = new THREE.MeshStandardMaterial({ color: sp.tailColor, roughness: 0.3, metalness: 0.5 });
    
    const fishGroup = new THREE.Group();
    const body = new THREE.Mesh(baseBodyGeo, bodyMat);
    // Gövdeyi türe göre şekillendir (Tombul, ince, uzun vs.)
    body.scale.set(sp.bodyScale[0], sp.bodyScale[2], sp.bodyScale[1]); 
    
    const tail = new THREE.Mesh(baseTailGeo, tailMat);
    
    fishGroup.add(body);
    fishGroup.add(tail);
    
    fishGroup.position.set((Math.random() - 0.5) * 80, (Math.random() - 0.5) * 50, (Math.random() - 0.5) * 40);
    
    // Doğduklarında çok küçükler (0.3 çarpanı)
    const initialScale = 0.3 + (Math.random() * 0.2);
    fishGroup.scale.set(initialScale, initialScale, initialScale);
    
    fishGroup.userData = {
        species: sp.name,
        velocity: new THREE.Vector3(Math.random()-0.5, Math.random()-0.5, Math.random()-0.5),
        speed: sp.speed + (Math.random() * 0.02),
        tailMesh: tail,
        seed: Math.random() * 100,
        currentScale: initialScale,
        targetScale: initialScale, // Büyüme hedefi
        maxScale: 1.5 // Ulaşabileceği maksimum boyut
    };
    
    scene.add(fishGroup);
    fishes.push(fishGroup);
}

// 5. TANE TANE YEM ATMA (Küçük boyut, birden fazla)
const raycaster = new THREE.Raycaster();
const mouse = new THREE.Vector2();

window.addEventListener('pointerdown', (event) => {
    mouse.x = (event.clientX / window.innerWidth) * 2 - 1;
    mouse.y = -(event.clientY / window.innerHeight) * 2 + 1;
    raycaster.setFromCamera(mouse, camera);
    
    // Yem boyutunu çok küçülttük (0.15) ve detayını azalttık ki kasmadan yüzlercesi atılabilsin
    const foodGeo = new THREE.SphereGeometry(0.15, 8, 8); 
    const foodMat = new THREE.MeshStandardMaterial({ color: 0xffd700, roughness: 0.4, emissive: 0xaa5500 });
    const foodMesh = new THREE.Mesh(foodGeo, foodMat);
    
    // Kameranın baktığı yönde 20 birim ileriye bırak
    raycaster.ray.at(20, foodMesh.position);
    
    // Yeme biraz rastgele dağılma payı ekle (Aynı yere tıklanırsa üst üste binmesin)
    foodMesh.position.x += (Math.random() - 0.5) * 2;
    foodMesh.position.y += (Math.random() - 0.5) * 2;
    
    scene.add(foodMesh);
    foods.push(foodMesh); // Diziye ekle
});

// 6. PENCERE İLLÜZYONU
let targetCameraX = 0;
let targetCameraY = 0;

function handleOrientation(event) {
    let gamma = event.gamma; 
    let beta = event.beta;   
    if (gamma > 45) gamma = 45; if (gamma < -45) gamma = -45;
    if (beta > 85) beta = 85; if (beta < 5) beta = 5; 
    
    targetCameraX = gamma * 0.3;
    targetCameraY = (beta - 45) * 0.3; 
}

window.addEventListener('mousemove', (e) => {
    targetCameraX = (e.clientX / window.innerWidth - 0.5) * 20;
    targetCameraY = -(e.clientY / window.innerHeight - 0.5) * 20;
});

document.getElementById('start-btn').addEventListener('click', async () => {
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
        try {
            const permission = await DeviceOrientationEvent.requestPermission();
            if (permission === 'granted') window.addEventListener('deviceorientation', handleOrientation);
        } catch (error) { console.error(error); }
    } else {
        window.addEventListener('deviceorientation', handleOrientation);
    }
    document.getElementById('start-screen').style.opacity = '0';
    setTimeout(() => document.getElementById('start-screen').remove(), 500);
});

// 7. ANA ANİMASYON DÖNGÜSÜ
const clock = new THREE.Clock();

function animate() {
    requestAnimationFrame(animate);
    const elapsedTime = clock.getElapsedTime();

    particlesMesh.position.y = Math.sin(elapsedTime * 0.2) * 5;
    particlesMesh.rotation.y = elapsedTime * 0.03;

    camera.position.x += (targetCameraX - camera.position.x) * 0.05;
    camera.position.y += (targetCameraY - camera.position.y) * 0.05;
    camera.lookAt(0, 0, 0);

    // Yemleri Güncelle (Dibe batırma)
    for (let i = foods.length - 1; i >= 0; i--) {
        foods[i].position.y -= 0.05; // Yavaşça batar
        
        // Çok derine giden yemi sil (Performans için)
        if (foods[i].position.y < -40) {
            scene.remove(foods[i]);
            foods.splice(i, 1);
        }
    }

    // Balıkları Güncelle
    fishes.forEach(fish => {
        const data = fish.userData;
        let target = new THREE.Vector3();
        let closestFood = null;
        let closestDist = Infinity;

        // En yakın yemi bul
        for (let i = 0; i < foods.length; i++) {
            let dist = fish.position.distanceTo(foods[i].position);
            if (dist < closestDist) {
                closestDist = dist;
                closestFood = foods[i];
            }
        }

        if (closestFood) {
            target.copy(closestFood.position);
            
            // Yemi Yeme Anı (Mesafe çok yakınsa)
            if (closestDist < 1.5) {
                scene.remove(closestFood);
                foods.splice(foods.indexOf(closestFood), 1);
                
                // Balığın hedef boyutunu büyüt (Maksimum boyuta kadar)
                if(data.targetScale < data.maxScale) {
                    data.targetScale += 0.15; 
                }
            }
        } else {
            target.set(0, 0, 0); 
        }

        const direction = new THREE.Vector3().subVectors(target, fish.position);
        direction.normalize();
        
        if (!closestFood) {
            direction.add(new THREE.Vector3(
                Math.sin(elapsedTime * 0.8 + data.seed),
                Math.cos(elapsedTime * 0.5 + data.seed),
                Math.sin(elapsedTime * 1.2 + data.seed)
            ).multiplyScalar(0.7));
        }

        data.velocity.lerp(direction, 0.02);
        data.velocity.normalize().multiplyScalar(data.speed);
        fish.position.add(data.velocity);

        const lookTarget = fish.position.clone().add(data.velocity);
        fish.lookAt(lookTarget);

        // Kuyruk hızı (Yem kovalarken çok hızlı sallanır)
        const tailSpeed = closestFood ? 25 : 8; 
        data.tailMesh.rotation.y = Math.sin(elapsedTime * tailSpeed + data.seed) * 0.6;
        
        // Pürüzsüz Büyüme Animasyonu (Lerp kullanarak yavaşça şişer)
        data.currentScale += (data.targetScale - data.currentScale) * 0.02;
        fish.scale.set(data.currentScale, data.currentScale, data.currentScale);

        if (fish.position.length() > 60) {
            fish.position.normalize().multiplyScalar(59);
        }
    });

    renderer.render(scene, camera);
}

window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
});

animate();
