/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.vet;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Spring MVC controller responsible for handling all HTTP requests related to
 * {@link Vet} listings. Exposes two endpoints: a paginated HTML view of all
 * veterinarians, and a JSON/XML resource endpoint that returns the full vet list
 * for programmatic consumption.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Ken Krebs
 * @author Arjen Poutsma
 */
@Controller
class VetController {

	/** Number of vet records displayed per page in the HTML list view. */
	private static final int PAGE_SIZE = 5;

	private final VetRepository vetRepository;

	public VetController(VetRepository vetRepository) {
		this.vetRepository = vetRepository;
	}

	/**
	 * Displays a paginated HTML list of all veterinarians. A {@link Vets} wrapper
	 * object is used rather than a raw collection to simplify Object-XML mapping
	 * should the view be rendered as XML.
	 * @param page the 1-based page number to display, defaults to 1
	 * @param model the Spring MVC model used to pass pagination data to the view
	 * @return the logical view name for the vet list page
	 */
	@GetMapping("/vets.html")
	public String showVetList(@RequestParam(defaultValue = "1") int page, Model model) {
		// Here we are returning an object of type 'Vets' rather than a collection of Vet
		// objects so it is simpler for Object-Xml mapping
		Vets vets = new Vets();
		Page<Vet> paginated = findPaginated(page);
		vets.getVetList().addAll(paginated.toList());
		return addPaginationModel(page, paginated, model);
	}

	/**
	 * Populates the model with pagination metadata and the current page of vets,
	 * then returns the vet list view.
	 * @param page the current 1-based page number
	 * @param paginated the paginated query result
	 * @param model the Spring MVC model to populate
	 * @return the logical view name for the vet list page
	 */
	private String addPaginationModel(int page, Page<Vet> paginated, Model model) {
		List<Vet> listVets = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listVets", listVets);
		return "vets/vetList";
	}

	/**
	 * Retrieves a single page of veterinarians from the repository.
	 * @param page the 1-based page number requested
	 * @return a {@link Page} of {@link Vet} instances
	 */
	private Page<Vet> findPaginated(int page) {
		Pageable pageable = PageRequest.of(page - 1, PAGE_SIZE);
		return vetRepository.findAll(pageable);
	}

	/**
	 * Returns the full list of veterinarians as a JSON or XML resource. A
	 * {@link Vets} wrapper object is used rather than a raw collection to simplify
	 * JSON and XML object mapping.
	 * @return a {@link Vets} instance containing all veterinarians
	 */
	@GetMapping({ "/vets" })
	public @ResponseBody Vets showResourcesVetList() {
		// Here we are returning an object of type 'Vets' rather than a collection of Vet
		// objects so it is simpler for JSon/Object mapping
		Vets vets = new Vets();
		vets.getVetList().addAll(this.vetRepository.findAll());
		return vets;
	}

}